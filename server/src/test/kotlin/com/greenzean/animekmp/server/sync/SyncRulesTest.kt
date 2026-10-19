package com.greenzean.animekmp.server.sync

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncRulesTest {
    private val t0 = Instant.parse("2026-10-19T12:00:00Z")

    @Test
    fun newerWinsTiesAndOlderLose() {
        assertTrue(isNewer(t0.plusMillis(1), t0))
        assertFalse(isNewer(t0, t0))
        assertFalse(isNewer(t0.minusMillis(1), t0))
        assertTrue(isNewer(t0, null))
    }

    @Test
    fun cursorRoundTripsAndStartsAtZero() {
        assertEquals(0L, Cursor.decode(null))
        assertEquals(0L, Cursor.decode(""))
        assertEquals(42L, Cursor.decode(Cursor.encode(42L)))
    }

    @Test
    fun invalidCursorIsRejected() {
        assertFailsWith<IllegalArgumentException> { Cursor.decode("abc") }
        assertFailsWith<IllegalArgumentException> { Cursor.decode("-1") }
    }
}
