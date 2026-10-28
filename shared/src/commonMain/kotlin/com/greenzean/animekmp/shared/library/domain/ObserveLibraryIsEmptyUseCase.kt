package com.greenzean.animekmp.shared.library.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveLibraryIsEmptyUseCase(
    private val local: LibraryLocalDataSource,
) {
    operator fun invoke(): Flow<Boolean> = local.observeActiveCount().map { it == 0 }
}
