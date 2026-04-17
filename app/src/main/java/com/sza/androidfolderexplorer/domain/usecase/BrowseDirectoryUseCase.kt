package com.sza.androidfolderexplorer.domain.usecase

import com.sza.androidfolderexplorer.domain.model.FileItem
import com.sza.androidfolderexplorer.domain.repository.FileRepository
import javax.inject.Inject

class BrowseDirectoryUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(path: String): Result<List<FileItem>> =
        repository.listDirectory(path)
}
