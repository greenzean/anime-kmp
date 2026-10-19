package com.greenzean.animekmp.shared.library.domain

import kotlin.time.Instant

data class Anime(
    val id: Long,
    val malId: Int,
    val title: String,
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
