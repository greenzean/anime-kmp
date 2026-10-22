package com.greenzean.animekmp.server.sync

import java.time.Instant
import java.util.UUID

enum class PushOutcome {
    APPLIED,
    IGNORED_STALE,
    REJECTED_INVALID,
}

/** Last-write-wins (ADR 0006): apply only if strictly newer than the stored row, so ties and replays are no-ops. */
fun isNewer(
    incoming: Instant,
    stored: Instant?,
): Boolean = stored == null || incoming.isAfter(stored)

/** The cursor is an opaque string on the wire; today it is the decimal `server_seq` of the last row returned. */
object Cursor {
    const val START = 0L

    fun decode(cursor: String?): Long {
        if (cursor.isNullOrEmpty()) return START
        return cursor.toLongOrNull()?.takeIf { it >= 0 } ?: throw IllegalArgumentException("Invalid cursor")
    }

    fun encode(seq: Long): String = seq.toString()
}

/** Placeholder until auth lands. Mirrors `LOCAL_USER_ID` in shared; auth replaces this. */
object DevUser {
    val id: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
}
