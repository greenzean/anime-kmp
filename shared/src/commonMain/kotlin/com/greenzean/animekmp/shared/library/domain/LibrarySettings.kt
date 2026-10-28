package com.greenzean.animekmp.shared.library.domain

import kotlinx.coroutines.flow.StateFlow

interface LibrarySettings {
    val sort: StateFlow<LibrarySort>

    fun setSort(sort: LibrarySort)
}
