package com.sza.androidfolderexplorer.domain.usecase

import com.sza.androidfolderexplorer.domain.repository.FileRepository
import javax.inject.Inject

class DeleteFilesUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(paths: List<String>): Result<Int> =
        repository.deleteFiles(paths)
}
