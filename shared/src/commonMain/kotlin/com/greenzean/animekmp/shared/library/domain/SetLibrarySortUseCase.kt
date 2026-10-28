package com.greenzean.animekmp.shared.library.domain

class SetLibrarySortUseCase(
    private val settings: LibrarySettings,
) {
    operator fun invoke(sort: LibrarySort) = settings.setSort(sort)
}
