package com.sza.androidfolderexplorer.presentation.browser

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.androidfolderexplorer.data.repository.RestrictedAccessException
import com.sza.androidfolderexplorer.data.source.SafFileAccess
import com.sza.androidfolderexplorer.data.source.ShizukuFileAccess
import com.sza.androidfolderexplorer.domain.usecase.BrowseDirectoryUseCase
import com.sza.androidfolderexplorer.domain.usecase.CopyFilesUseCase
import com.sza.androidfolderexplorer.domain.usecase.DeleteFilesUseCase
import com.sza.androidfolderexplorer.domain.usecase.MoveFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    @ApplicationContext private val context: Context,
    private val browseDirectory: BrowseDirectoryUseCase,
    private val copyFiles: CopyFilesUseCase,
    private val moveFiles: MoveFilesUseCase,
    private val deleteFiles: DeleteFilesUseCase,
    val safFileAccess: SafFileAccess,
    val shizukuFileAccess: ShizukuFileAccess
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private val prefs = context.getSharedPreferences("afex_prefs", Context.MODE_PRIVATE)

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
        val lastPath = prefs.getString("last_path", FileBrowserUiState.DEFAULT_PATH)
            ?: FileBrowserUiState.DEFAULT_PATH
        navigateTo(lastPath)
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
                    prefs.edit().putString("last_path", path).apply()
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
            // Android 11-13 with MANAGE_EXTERNAL_STORAGE already granted but File API still denied:
            // device (e.g. Samsung Knox) blocks it despite the permission — fall back to Shizuku
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() -> when {
                shizukuFileAccess.isServiceRunning() -> AccessPromptState.ShizukuPermissionRequired(path)
                shizukuFileAccess.isApkInstalled()   -> AccessPromptState.ShizukuNotRunning(path)
                else                                  -> AccessPromptState.ShizukuNotInstalled(path)
            }
            // Android 11-13 without MANAGE_EXTERNAL_STORAGE — ask the user to grant it
            else -> AccessPromptState.ManageStorageRequired(path)
        }

    private fun executeOperation(isMove: Boolean) {
        if (operationJob?.isActive == true) {
            Log.w("AFEX_MOVE", "executeOperation: operation already running, ignoring duplicate call")
            return
        }
        val selected = _uiState.value.selectedPaths.toList()
        Log.d("AFEX_MOVE", "executeOperation isMove=$isMove selected=$selected")
        if (selected.isEmpty()) {
            Log.w("AFEX_MOVE", "executeOperation: selection is empty, aborting")
            return
        }

        operationJob = viewModelScope.launch {
            Log.d("AFEX_MOVE", "coroutine started, collecting flow...")
            val flow = if (isMove) moveFiles(selected, downloadsPath)
                       else copyFiles(selected, downloadsPath)

            flow.catch { e ->
                Log.e("AFEX_MOVE", "flow.catch error", e)
                _uiState.value = _uiState.value.copy(
                    operation = OperationState.Failed(e.message ?: "Operation failed")
                )
            }
            .onCompletion { error ->
                Log.d("AFEX_MOVE", "onCompletion error=$error")
                if (error == null) {
                    val action = if (isMove) "Moved" else "Copied"
                    _uiState.value = _uiState.value.copy(
                        operation = OperationState.Completed("$action ${selected.size} item(s) to Downloads"),
                        selectedPaths = emptySet()
                    )
                }
            }
            .collect { progress ->
                Log.d("AFEX_MOVE", "progress: ${progress.processedFiles}/${progress.totalFiles} ${progress.currentFileName}")
                _uiState.value = _uiState.value.copy(operation = OperationState.InProgress(progress))
            }

            // After Move: refresh directory so moved items disappear from the list
            if (isMove && _uiState.value.operation is OperationState.Completed) {
                delay(1200)
                val currentPath = _uiState.value.currentPath
                _uiState.value = _uiState.value.copy(isLoading = true, operation = null)
                browseDirectory(currentPath)
                    .onSuccess { items ->
                        _uiState.value = _uiState.value.copy(items = items, isLoading = false)
                    }
                    .onFailure {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
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

