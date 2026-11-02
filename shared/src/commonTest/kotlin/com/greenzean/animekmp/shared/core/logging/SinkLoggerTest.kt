package com.greenzean.animekmp.shared.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SinkLoggerTest {
    @Test
    fun everySinkReceivesTheEntry() {
        val first = RecordingLogSink()
        val second = RecordingLogSink()
        val boom = IllegalStateException("x")

        SinkLogger(listOf(first, second)).error("Tag", "message", boom)

        val expected = LogEntry(LogLevel.Error, "Tag", "message", boom)
        assertEquals(listOf(expected), first.entries)
        assertEquals(listOf(expected), second.entries)
        assertSame(boom, first.entries.single().throwable)
    }

    @Test
    fun helpersUseTheirLevel() {
        val sink = RecordingLogSink()
        val logger = SinkLogger(listOf(sink))

        logger.debug("T", "d")
        logger.info("T", "i")
        logger.warn("T", "w")
        logger.error("T", "e")

        assertEquals(
            listOf(LogLevel.Debug, LogLevel.Info, LogLevel.Warn, LogLevel.Error),
            sink.entries.map { it.level },
        )
    }

    @Test
    fun aSinkUnderItsMinLevelReceivesNothing() {
        val strict = RecordingLogSink(minLevel = LogLevel.Warn)
        val open = RecordingLogSink(minLevel = LogLevel.Debug)
        val logger = SinkLogger(listOf(strict, open))

        logger.debug("T", "d")
        logger.info("T", "i")
        logger.warn("T", "w")
        logger.error("T", "e")

        assertEquals(listOf("w", "e"), strict.entries.map { it.message })
        assertEquals(4, open.entries.size)
    }

    @Test
    fun aSinkAtExactlyItsMinLevelReceivesTheEntry() {
        val sink = RecordingLogSink(minLevel = LogLevel.Info)

        SinkLogger(listOf(sink)).info("T", "i")

        assertEquals(1, sink.entries.size)
    }

    @Test
    fun aThrowingSinkDoesNotStopTheOtherSinksOrTheCaller() {
        val throwing = ThrowingLogSink()
        val after = RecordingLogSink()
        val before = RecordingLogSink()
        val logger = SinkLogger(listOf(before, throwing, after))

        logger.warn("T", "first")
        logger.warn("T", "second")

        assertEquals(2, throwing.calls)
        assertEquals(listOf("first", "second"), before.entries.map { it.message })
        assertEquals(listOf("first", "second"), after.entries.map { it.message })
    }

    @Test
    fun noSinksIsFine() {
        SinkLogger(emptyList()).error("T", "nobody listens")
        assertTrue(true)
    }
}
