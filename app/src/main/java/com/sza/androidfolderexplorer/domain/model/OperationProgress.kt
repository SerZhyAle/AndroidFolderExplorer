package com.sza.androidfolderexplorer.domain.model

data class OperationProgress(
    val totalFiles: Int,
    val processedFiles: Int,
    val currentFileName: String,
    val totalBytes: Long,
    val processedBytes: Long
) {
    val fraction: Float
        get() = if (totalBytes > 0) processedBytes.toFloat() / totalBytes else 0f

    val isComplete: Boolean
        get() = processedFiles == totalFiles && totalFiles > 0
}
