package com.greenzean.animekmp.shared.library.domain

import kotlinx.coroutines.flow.Flow

class ObserveLibraryUseCase(
    private val local: LibraryLocalDataSource,
) {
    operator fun invoke(): Flow<List<LibraryEntry>> = local.observeLibrary()
}
