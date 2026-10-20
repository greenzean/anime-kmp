package com.greenzean.animekmp.shared.library.presentation

import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus

data class EntryUi(
    val id: String,
    val title: String,
    val status: WatchStatus,
    val syncState: SyncState,
)

data class AnimeUi(
    val id: Long,
    val title: String,
)

fun LibraryEntry.toEntryUi(): EntryUi = EntryUi(id = id, title = anime.title, status = status, syncState = syncState)

fun Anime.toAnimeUi(): AnimeUi = AnimeUi(id = id, title = title)
