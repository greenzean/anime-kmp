package com.greenzean.animekmp.server.sync

import java.time.Instant

const val MAX_PROGRESS = 10000
const val MIN_SCORE = 1
const val MAX_SCORE = 10

enum class PushOutcome {
    APPLIED,
    IGNORED_STALE,
    REJECTED_INVALID,
}

fun isNewer(
    incoming: Instant,
    stored: Instant?,
): Boolean = stored == null || incoming.isAfter(stored)

object Cursor {
    const val START = 0L

    fun decode(cursor: String?): Long {
        if (cursor.isNullOrEmpty()) return START
        return cursor.toLongOrNull()?.takeIf { it >= 0 } ?: throw IllegalArgumentException("Invalid cursor")
    }

    fun encode(seq: Long): String = seq.toString()
}
