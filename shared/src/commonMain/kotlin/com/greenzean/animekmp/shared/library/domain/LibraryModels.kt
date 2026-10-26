package com.greenzean.animekmp.shared.library.domain

import kotlin.time.Instant

data class Anime(
    val malId: Int,
    val title: String,
    val titleEn: String? = null,
    val imageUrl: String? = null,
)

enum class WatchStatus { WATCHING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_WATCH }

enum class SyncState { PENDING, SYNCED }

data class LibraryEntry(
    val id: String,
    val anime: Anime,
    val status: WatchStatus,
    val updatedAt: Instant,
    val syncState: SyncState,
)
