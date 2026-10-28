package com.greenzean.animekmp.shared.settings

import com.greenzean.animekmp.shared.library.domain.LibrarySettings
import com.greenzean.animekmp.shared.library.domain.LibrarySort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeLibrarySettings(
    initial: LibrarySort = LibrarySort.LAST_UPDATED,
) : LibrarySettings {
    private val current = MutableStateFlow(initial)

    override val sort: StateFlow<LibrarySort> = current

    override fun setSort(sort: LibrarySort) {
        current.value = sort
    }
}
