package com.greenzean.animekmp.server.sync

import com.greenzean.animekmp.server.InvalidInput
import java.time.Instant

const val MAX_PROGRESS = 10000
const val MIN_SCORE = 1
const val MAX_SCORE = 10
const val MAX_MAL_ID = 1_000_000
const val MAX_ENTRIES_PER_USER = 20_000
val MIN_TIMESTAMP: Instant = Instant.parse("2000-01-01T00:00:00Z")

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
        return cursor.toLongOrNull()?.takeIf { it >= 0 } ?: throw InvalidInput("Invalid cursor")
    }

    fun encode(seq: Long): String = seq.toString()
}
