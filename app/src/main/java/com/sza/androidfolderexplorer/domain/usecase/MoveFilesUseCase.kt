package com.sza.androidfolderexplorer.domain.usecase

import com.sza.androidfolderexplorer.domain.model.OperationProgress
import com.sza.androidfolderexplorer.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MoveFilesUseCase @Inject constructor(
    private val repository: FileRepository
) {
    operator fun invoke(sources: List<String>, destination: String): Flow<OperationProgress> =
        repository.moveFiles(sources, destination)
}
