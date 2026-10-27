package com.greenzean.animekmp.shared.library.presentation

import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import com.greenzean.animekmp.shared.settings.domain.pick

data class EntryUi(
    val id: String,
    val malId: Int,
    val title: String,
    val imageUrl: String?,
    val status: WatchStatus,
    val syncState: SyncState,
)

fun LibraryEntry.toEntryUi(language: TitleLanguage): EntryUi =
    EntryUi(
        id = id,
        malId = anime.malId,
        title = language.pick(anime.title, anime.titleEn),
        imageUrl = anime.imageUrl,
        status = status,
        syncState = syncState,
    )
