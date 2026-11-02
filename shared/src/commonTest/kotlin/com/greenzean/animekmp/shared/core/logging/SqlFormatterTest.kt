package com.greenzean.animekmp.shared.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlFormatterTest {
    @Test
    fun whitespaceIsCollapsed() {
        val result = SqlFormatter.format("SELECT  id,\n   name\n\tFROM   anime   WHERE id = 1", emptyList())

        assertEquals("SELECT id, name FROM anime WHERE id = 1", result.text)
    }

    @Test
    fun aSelectIsAReadOnItsTable() {
        val result = SqlFormatter.format("SELECT id, name FROM list_entry ORDER BY id", emptyList())

        assertEquals(SqlKind.Read, result.kind)
        assertEquals("list_entry", result.table)
    }

    @Test
    fun anInsertOrReplaceIsAWriteOnItsTable() {
        val result = SqlFormatter.format("INSERT OR REPLACE INTO anime (id, title) VALUES (?, ?)", listOf(1L, "a"))

        assertEquals(SqlKind.Write, result.kind)
        assertEquals("anime", result.table)
    }

    @Test
    fun anUpdateIsAWriteOnItsTable() {
        val result = SqlFormatter.format("UPDATE list_entry SET score = ? WHERE id = ?", listOf(8L, "x"))

        assertEquals(SqlKind.Write, result.kind)
        assertEquals("list_entry", result.table)
    }

    @Test
    fun aDeleteIsAWriteOnItsTable() {
        val result = SqlFormatter.format("DELETE FROM list_entry WHERE id = ?", listOf("x"))

        assertEquals(SqlKind.Write, result.kind)
        assertEquals("list_entry", result.table)
    }

    @Test
    fun createDropAndAlterAreWrites() {
        listOf("CREATE TABLE t (id INTEGER)", "DROP TABLE t", "ALTER TABLE t ADD COLUMN x TEXT").forEach {
            assertEquals(SqlKind.Write, SqlFormatter.format(it, emptyList()).kind, it)
        }
    }

    @Test
    fun aStatementWithoutATableHasANullTable() {
        val result = SqlFormatter.format("SELECT 1", emptyList())

        assertEquals(SqlKind.Read, result.kind)
        assertNull(result.table)
    }

    @Test
    fun questionMarksAreReplacedInOrderByTheValues() {
        val result =
            SqlFormatter.format(
                "INSERT INTO anime (id, title, score) VALUES (?, ?, ?)",
                listOf(7L, "Naruto", 8.5),
            )

        assertTrue("VALUES (7, 'Naruto', 8.5)" in result.text, result.text)
    }

    @Test
    fun numberedPlaceholdersAreReplacedInOrder() {
        val result = SqlFormatter.format("UPDATE anime SET title = ?1 WHERE id = ?2", listOf("A", 3L))

        assertTrue("title = 'A' WHERE id = 3" in result.text, result.text)
    }

    @Test
    fun nullBooleansAndBytesAreRendered() {
        val result =
            SqlFormatter.format(
                "INSERT INTO t (a, b, c, d) VALUES (?, ?, ?, ?)",
                listOf(null, true, false, ByteArray(5)),
            )

        assertTrue("VALUES (NULL, true, false, <5 bytes>)" in result.text, result.text)
    }

    @Test
    fun quotesInStringsAreDoubled() {
        val result = SqlFormatter.format("INSERT INTO t (a) VALUES (?)", listOf("it's"))

        assertTrue("'it''s'" in result.text, result.text)
    }

    @Test
    fun aLongStringIsCutWithItsLength() {
        val value = "x".repeat(200)

        val result = SqlFormatter.format("INSERT INTO t (a) VALUES (?)", listOf(value))

        assertFalse("x".repeat(78) in result.text, result.text)
        assertTrue("x".repeat(77) + "...'" in result.text, result.text)
        assertTrue("(200 chars)" in result.text, result.text)
    }

    @Test
    fun aVeryLongStringShowsItsSizeInKb() {
        val value = "x".repeat(2048)

        val result = SqlFormatter.format("INSERT INTO t (a) VALUES (?)", listOf(value))

        assertTrue("...'" in result.text, result.text)
        assertTrue("KB)" in result.text, result.text)
        assertFalse("x".repeat(100) in result.text, result.text)
    }

    @Test
    fun aShortStringIsNotCut() {
        val result = SqlFormatter.format("INSERT INTO t (a) VALUES (?)", listOf("x".repeat(60)))

        assertTrue("'" + "x".repeat(60) + "'" in result.text, result.text)
        assertFalse("chars)" in result.text, result.text)
    }

    @Test
    fun aPlaceholderCountMismatchKeepsTheSqlAndAppendsTheValues() {
        val result = SqlFormatter.format("SELECT * FROM anime WHERE id = ? AND x = ?", listOf(1L))

        assertTrue(result.text.startsWith("SELECT * FROM anime WHERE id = ? AND x = ?"), result.text)
        assertTrue(" [values: " in result.text, result.text)
        assertTrue(result.text.endsWith("]"), result.text)
    }

    @Test
    fun aLongColumnListIsSummarised() {
        val columns = (1..20).joinToString(", ") { "column_number_$it" }

        val result = SqlFormatter.format("SELECT $columns FROM anime WHERE id = ?", listOf(1L))

        assertTrue("SELECT 20 columns FROM" in result.text, result.text)
        assertFalse("column_number_1," in result.text, result.text)
        assertTrue("WHERE id = 1" in result.text, result.text)
        assertEquals("anime", result.table)
    }

    @Test
    fun aShortColumnListIsKept() {
        val result = SqlFormatter.format("SELECT id, name FROM anime", emptyList())

        assertEquals("SELECT id, name FROM anime", result.text)
    }
}
