package com.greenzean.animekmp.shared.library.domain

import kotlinx.coroutines.flow.StateFlow

class ObserveLibrarySortUseCase(
    private val settings: LibrarySettings,
) {
    operator fun invoke(): StateFlow<LibrarySort> = settings.sort
}
