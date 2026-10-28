package com.greenzean.animekmp.shared.library.domain

import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import kotlinx.coroutines.flow.Flow

class ObserveLibraryUseCase(
    private val local: LibraryLocalDataSource,
) {
    operator fun invoke(
        query: String = "",
        status: WatchStatus? = null,
        sort: LibrarySort = LibrarySort.LAST_UPDATED,
        titleLanguage: TitleLanguage = TitleLanguage.MAIN,
    ): Flow<List<LibraryEntry>> = local.observeLibrary(query, status, sort, titleLanguage)
}
