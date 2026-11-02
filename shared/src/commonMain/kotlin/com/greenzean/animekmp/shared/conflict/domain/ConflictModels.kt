package com.greenzean.animekmp.shared.conflict.domain

import com.greenzean.animekmp.shared.library.domain.WatchStatus

data class EntryValues(
    val status: WatchStatus,
    val progress: Int,
    val score: Int?,
    val removed: Boolean,
)

data class Conflict(
    val malId: Int,
    val title: String,
    val episodes: Int?,
    val app: EntryValues,
    val other: EntryValues,
    val imageUrl: String? = null,
)

enum class ConflictSide { App, Other }
