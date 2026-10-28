package com.greenzean.animekmp.shared.library.data

import android.content.Context
import com.greenzean.animekmp.shared.library.domain.LibrarySettings
import com.greenzean.animekmp.shared.library.domain.LibrarySort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val FILE = "library_settings"
private const val KEY_SORT = "sort"

class SharedPreferencesLibrarySettings(
    context: Context,
) : LibrarySettings {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val current = MutableStateFlow(read())

    override val sort: StateFlow<LibrarySort> = current.asStateFlow()

    override fun setSort(sort: LibrarySort) {
        prefs.edit().putString(KEY_SORT, sort.name).apply()
        current.value = sort
    }

    private fun read(): LibrarySort =
        prefs
            .getString(KEY_SORT, null)
            ?.let { name -> LibrarySort.entries.firstOrNull { it.name == name } }
            ?: LibrarySort.LAST_UPDATED
}
