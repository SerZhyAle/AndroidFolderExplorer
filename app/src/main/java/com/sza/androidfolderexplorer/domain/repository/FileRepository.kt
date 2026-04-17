package com.sza.androidfolderexplorer.domain.repository

import com.sza.androidfolderexplorer.domain.model.FileItem
import com.sza.androidfolderexplorer.domain.model.OperationProgress
import kotlinx.coroutines.flow.Flow

interface FileRepository {
    suspend fun listDirectory(path: String): Result<List<FileItem>>
    fun copyFiles(sources: List<String>, destination: String): Flow<OperationProgress>
    fun moveFiles(sources: List<String>, destination: String): Flow<OperationProgress>
    suspend fun deleteFiles(paths: List<String>): Result<Int>
}
