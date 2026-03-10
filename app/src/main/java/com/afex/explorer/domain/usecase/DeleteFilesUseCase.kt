package com.afex.explorer.domain.usecase

import com.afex.explorer.domain.repository.FileRepository
import javax.inject.Inject

class DeleteFilesUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(paths: List<String>): Result<Int> =
        repository.deleteFiles(paths)
}
