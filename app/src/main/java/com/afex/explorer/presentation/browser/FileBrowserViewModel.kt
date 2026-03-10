package com.afex.explorer.presentation.browser

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afex.explorer.data.repository.RestrictedAccessException
import com.afex.explorer.data.source.SafFileAccess
import com.afex.explorer.data.source.ShizukuFileAccess
import com.afex.explorer.domain.usecase.BrowseDirectoryUseCase
import com.afex.explorer.domain.usecase.CopyFilesUseCase
import com.afex.explorer.domain.usecase.DeleteFilesUseCase
import com.afex.explorer.domain.usecase.MoveFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import javax.inject.Inject

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val browseDirectory: BrowseDirectoryUseCase,
    private val copyFiles: CopyFilesUseCase,
    private val moveFiles: MoveFilesUseCase,
    private val deleteFiles: DeleteFilesUseCase,
    val safFileAccess: SafFileAccess,
    val shizukuFileAccess: ShizukuFileAccess
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private var operationJob: Job? = null

    // Stored to retry navigation after permission grant
    private var pendingPath: String? = null

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            _uiState.value = _uiState.value.copy(accessPrompt = null)
            pendingPath?.let { navigateTo(it) }
        }
    }

    private val downloadsPath: String
        get() = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).absolutePath

    init {
        shizukuFileAccess.addPermissionListener(shizukuPermissionListener)
        navigateTo(FileBrowserUiState.DEFAULT_PATH)
    }

    override fun onCleared() {
        super.onCleared()
        shizukuFileAccess.removePermissionListener(shizukuPermissionListener)
    }

    fun navigateTo(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                selectedPaths = emptySet(),
                accessPrompt = null
            )
            browseDirectory(path)
                .onSuccess { items ->
                    pendingPath = null
                    _uiState.value = _uiState.value.copy(
                        currentPath = path,
                        items = items,
                        isLoading = false,
                        pathSegments = buildPathSegments(path)
                    )
                }
                .onFailure { error ->
                    if (error is RestrictedAccessException) {
                        pendingPath = error.path
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            accessPrompt = resolveAccessPrompt(error.path)
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error"
                        )
                    }
                }
        }
    }

    fun navigateUp(): Boolean {
        val current = _uiState.value.currentPath
        val parent = current.substringBeforeLast('/')
        if (parent.isEmpty() || parent == current) return false
        navigateTo(parent)
        return true
    }

    fun toggleSelection(path: String) {
        val current = _uiState.value.selectedPaths
        _uiState.value = _uiState.value.copy(
            selectedPaths = if (path in current) current - path else current + path
        )
    }

    fun toggleSelectAll() {
        val state = _uiState.value
        _uiState.value = if (state.allSelected) {
            state.copy(selectedPaths = emptySet())
        } else {
            state.copy(selectedPaths = state.items.map { it.path }.toSet())
        }
    }

    fun copyToDownloads() = executeOperation(isMove = false)
    fun moveToDownloads() = executeOperation(isMove = true)

    fun requestDelete() {
        if (_uiState.value.selectedPaths.isEmpty()) return
        _uiState.value = _uiState.value.copy(pendingDelete = true)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pendingDelete = false)
    }

    fun confirmDelete() {
        val selected = _uiState.value.selectedPaths.toList()
        _uiState.value = _uiState.value.copy(pendingDelete = false)
        viewModelScope.launch {
            deleteFiles(selected)
                .onSuccess { count ->
                    _uiState.value = _uiState.value.copy(
                        operation = OperationState.Completed("Deleted $count item(s)"),
                        selectedPaths = emptySet()
                    )
                    navigateTo(_uiState.value.currentPath)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        operation = OperationState.Failed(e.message ?: "Delete failed")
                    )
                }
        }
    }

    fun cancelOperation() {
        operationJob?.cancel()
        operationJob = null
        _uiState.value = _uiState.value.copy(operation = null)
    }

    fun dismissOperation() {
        _uiState.value = _uiState.value.copy(operation = null)
        navigateTo(_uiState.value.currentPath)
    }

    fun dismissAccessPrompt() {
        _uiState.value = _uiState.value.copy(accessPrompt = null)
    }

    /** Re-checks access and retries the last pending path (called on Activity resume). */
    fun retryPendingPath() {
        pendingPath?.let { navigateTo(it) }
    }

    /** Called by Screen after user picks a folder in SAF document tree picker. */
    fun onSafPermissionGranted(uri: Uri) {
        safFileAccess.persistTreeUri(uri)
        _uiState.value = _uiState.value.copy(accessPrompt = null)
        pendingPath?.let { navigateTo(it) }
    }

    /** Called by Screen to trigger Shizuku permission request dialog. */
    fun requestShizukuPermission() = shizukuFileAccess.requestPermission()

    /** Returns the initial URI hint for the SAF document tree picker. */
    fun buildSafInitialUri(path: String) = safFileAccess.buildSafInitialUri(path)

    private fun resolveAccessPrompt(path: String): AccessPromptState =
        when {
            // Android 14+ — FUSE blocks even MANAGE_EXTERNAL_STORAGE; Shizuku is the reliable path
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> when {
                shizukuFileAccess.isServiceRunning() -> AccessPromptState.ShizukuPermissionRequired(path)
                shizukuFileAccess.isApkInstalled()   -> AccessPromptState.ShizukuNotRunning(path)
                else                                  -> AccessPromptState.ShizukuNotInstalled(path)
            }
            // Android 11-13 — SAF tree URI works for Android/data
            else -> AccessPromptState.SafRequired(path)
        }

    private fun executeOperation(isMove: Boolean) {
        val selected = _uiState.value.selectedPaths.toList()
        if (selected.isEmpty()) return

        operationJob = viewModelScope.launch {
            val flow = if (isMove) moveFiles(selected, downloadsPath)
                       else copyFiles(selected, downloadsPath)

            flow.catch { e ->
                _uiState.value = _uiState.value.copy(
                    operation = OperationState.Failed(e.message ?: "Operation failed")
                )
            }
            .onCompletion { error ->
                if (error == null) {
                    val action = if (isMove) "Moved" else "Copied"
                    _uiState.value = _uiState.value.copy(
                        operation = OperationState.Completed("$action ${selected.size} item(s) to Downloads"),
                        selectedPaths = emptySet()
                    )
                }
            }
            .collect { progress ->
                _uiState.value = _uiState.value.copy(operation = OperationState.InProgress(progress))
            }
        }
    }

    private fun buildPathSegments(path: String): List<PathSegment> {
        val parts = path.removePrefix("/").split("/")
        val segments = mutableListOf<PathSegment>()
        var accumulated = ""
        for (part in parts) {
            accumulated = "$accumulated/$part"
            segments.add(PathSegment(part, accumulated))
        }
        return segments
    }
}

