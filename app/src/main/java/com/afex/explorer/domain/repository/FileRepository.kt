package com.afex.explorer.domain.repository

import com.afex.explorer.domain.model.FileItem
import com.afex.explorer.domain.model.OperationProgress
import kotlinx.coroutines.flow.Flow

interface FileRepository {
    suspend fun listDirectory(path: String): Result<List<FileItem>>
    fun copyFiles(sources: List<String>, destination: String): Flow<OperationProgress>
    fun moveFiles(sources: List<String>, destination: String): Flow<OperationProgress>
}
