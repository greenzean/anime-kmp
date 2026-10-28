package com.greenzean.animekmp.shared.library.data

import com.greenzean.animekmp.shared.core.data.db.List_entry
import com.greenzean.animekmp.shared.core.data.db.SelectActiveWithAnime
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import kotlin.time.Instant

fun SelectActiveWithAnime.toLibraryEntry(): LibraryEntry =
    LibraryEntry(
        id = id,
        anime =
            Anime(
                malId = mal_id.toInt(),
                title = title,
                titleEn = title_en,
                imageUrl = image_url,
                episodes = episodes?.toInt(),
            ),
        status = WatchStatus.valueOf(status),
        progress = progress.toInt(),
        score = score?.toInt(),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
        syncState = SyncState.valueOf(sync_state),
    )

fun List_entry.toLibraryEntry(anime: com.greenzean.animekmp.shared.core.data.db.Anime): LibraryEntry =
    LibraryEntry(
        id = id,
        anime =
            Anime(
                malId = anime.mal_id.toInt(),
                title = anime.title,
                titleEn = anime.title_en,
                imageUrl = anime.image_url,
                episodes = anime.episodes?.toInt(),
            ),
        status = WatchStatus.valueOf(status),
        progress = progress.toInt(),
        score = score?.toInt(),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
        syncState = SyncState.valueOf(sync_state),
    )
