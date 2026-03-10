package com.afex.explorer.domain.usecase

import com.afex.explorer.domain.model.FileItem
import com.afex.explorer.domain.repository.FileRepository
import javax.inject.Inject

class BrowseDirectoryUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(path: String): Result<List<FileItem>> =
        repository.listDirectory(path)
}
