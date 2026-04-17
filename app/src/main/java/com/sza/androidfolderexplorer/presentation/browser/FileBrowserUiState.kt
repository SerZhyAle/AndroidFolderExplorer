package com.sza.androidfolderexplorer.presentation.browser

import com.sza.androidfolderexplorer.domain.model.FileItem
import com.sza.androidfolderexplorer.domain.model.OperationProgress

data class FileBrowserUiState(
    val currentPath: String = DEFAULT_PATH,
    val items: List<FileItem> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val operation: OperationState? = null,
    val pathSegments: List<PathSegment> = emptyList(),
    val accessPrompt: AccessPromptState? = null,
    val pendingDelete: Boolean = false
) {
    val hasSelection: Boolean get() = selectedPaths.isNotEmpty()
    val allSelected: Boolean get() = items.isNotEmpty() && selectedPaths.size == items.size

    companion object {
        const val DEFAULT_PATH = "/storage/emulated/0/Android"
    }
}

data class PathSegment(val name: String, val fullPath: String)

sealed interface OperationState {
    data class InProgress(val progress: OperationProgress) : OperationState
    data class Completed(val message: String) : OperationState
    data class Failed(val error: String) : OperationState
}

/**
 * Represents what access permission the user needs to grant for a restricted path.
 * The path is stored so it can be retried after the user grants access.
 */
sealed interface AccessPromptState {
    val path: String
    /** Android 11-13: grant MANAGE_EXTERNAL_STORAGE ("All files access") in Settings */
    data class ManageStorageRequired(override val path: String) : AccessPromptState
    /** Legacy SAF fallback (not currently used as primary flow) */
    data class SafRequired(override val path: String) : AccessPromptState
    /** Android 14+: Shizuku service running, needs permission grant */
    data class ShizukuPermissionRequired(override val path: String) : AccessPromptState
    /** Android 14+: Shizuku APK installed but service is not running — user must start it */
    data class ShizukuNotRunning(override val path: String) : AccessPromptState
    /** Android 14+: Shizuku APK not installed — show install options */
    data class ShizukuNotInstalled(override val path: String) : AccessPromptState
}
