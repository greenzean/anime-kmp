package com.greenzean.animekmp.shared.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunkForLogcatTest {
    @Test
    fun aShortMessageIsOneChunk() {
        assertEquals(listOf("hello"), chunkForLogcat("hello"))
    }

    @Test
    fun aMessageAtTheLimitIsOneChunk() {
        val message = "a".repeat(3500)

        assertEquals(listOf(message), chunkForLogcat(message))
    }

    @Test
    fun anEmptyMessageStillProducesOneEmptyLine() {
        assertEquals(listOf(""), chunkForLogcat(""))
    }

    @Test
    fun aLongMessageIsSplitIntoChunksWithinTheLimit() {
        val message = "abcdefghij".repeat(1000)

        val chunks = chunkForLogcat(message, max = 3500)

        assertTrue(chunks.size >= 3, chunks.size.toString())
        assertTrue(chunks.all { it.length <= 3500 }, chunks.map { it.length }.toString())
        assertEquals(message, chunks.joinToString(""))
    }

    @Test
    fun aCustomLimitIsHonoured() {
        val message = "x".repeat(25)

        val chunks = chunkForLogcat(message, max = 10)

        assertTrue(chunks.all { it.length <= 10 })
        assertEquals(message, chunks.joinToString(""))
    }

    @Test
    fun noCharactersAreLostWhenSplittingMultilineText() {
        val message = (1..400).joinToString("\n") { "line $it with some padding text" }

        val chunks = chunkForLogcat(message, max = 500)

        assertTrue(chunks.all { it.length <= 500 }, chunks.map { it.length }.toString())
        assertEquals(message.replace("\n", ""), chunks.joinToString("").replace("\n", ""))
    }

    @Test
    fun splitsPreferNewlineBoundaries() {
        val lineA = "a".repeat(300)
        val lineB = "b".repeat(300)
        val message = "$lineA\n$lineB"

        val chunks = chunkForLogcat(message, max = 400)

        assertEquals(2, chunks.size)
        assertEquals(lineA, chunks[0].trimEnd('\n'))
        assertEquals(lineB, chunks[1].trimStart('\n'))
    }

    @Test
    fun aSingleLineLongerThanTheLimitIsStillSplit() {
        val message = "z".repeat(1000)

        val chunks = chunkForLogcat(message, max = 300)

        assertTrue(chunks.size >= 4)
        assertTrue(chunks.all { it.length in 1..300 })
        assertEquals(message, chunks.joinToString(""))
    }
}
