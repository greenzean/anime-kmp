package com.greenzean.animekmp.shared.library.data

import com.greenzean.animekmp.shared.core.data.db.SelectActiveWithAnime
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import kotlin.time.Instant
import com.greenzean.animekmp.shared.core.data.db.Anime as AnimeRow

fun AnimeRow.toAnime(): Anime = Anime(malId = mal_id.toInt(), title = title)

fun SelectActiveWithAnime.toLibraryEntry(): LibraryEntry =
    LibraryEntry(
        id = id,
        anime = Anime(malId = mal_id.toInt(), title = title),
        status = WatchStatus.valueOf(status),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
        syncState = SyncState.valueOf(sync_state),
    )
