package com.greenzean.animekmp.shared.core.logging

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.greenzean.animekmp.shared.core.data.createTestDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val CREATE = "CREATE TABLE log_probe (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)"
private const val INSERT = "INSERT INTO log_probe (id, name) VALUES (?, ?)"
private const val SELECT = "SELECT id, name FROM log_probe ORDER BY id"
private const val DELETE_ALL = "DELETE FROM log_probe"
private const val BAD_SQL = "SELECT * FROM table_that_does_not_exist"

private class Probe(
    driver: SqlDriver,
) : TransacterImpl(driver)

class LoggingSqlDriverTest {
    private val sink = RecordingLogSink(minLevel = LogLevel.Trace)
    private val logger = SinkLogger(listOf(sink))
    private val raw = createTestDriver()
    private val driver = LoggingSqlDriver(createTestDriver(), logger)

    init {
        driver.execute(null, CREATE, 0).value
        raw.execute(null, CREATE, 0).value
        sink.entries.clear()
    }

    private fun SqlDriver.insert(
        id: Long,
        name: String,
    ) = execute(null, INSERT, 2) {
        bindLong(0, id)
        bindString(1, name)
    }.value

    private fun SqlDriver.names(): List<String> =
        executeQuery(
            null,
            SELECT,
            { cursor ->
                val names = mutableListOf<String>()
                while (cursor.next().value) names += cursor.getString(1)!!
                QueryResult.Value(names)
            },
            0,
        ).value

    private fun sqlMessages() = sink.entries.filter { it.tag == SQL_TAG }.map { it.message }

    @Test
    fun everySqlEntryIsAtTraceLevel() {
        driver.insert(1, "a")
        driver.names()

        assertTrue(sink.entries.isNotEmpty())
        assertTrue(sink.entries.all { it.tag == SQL_TAG && it.level == LogLevel.Trace }, sink.entries.toString())
    }

    @Test
    fun anInsertLogsTheSqlTheBoundValuesAndTheChangedRows() {
        val changed = driver.insert(7, "SENTINEL-NAME-3c9f")

        assertEquals(1L, changed)
        val line = sqlMessages().single { "INSERT INTO log_probe" in it }
        assertTrue(line.startsWith("SQL  WRITE  log_probe"), line)
        assertTrue("INSERT INTO log_probe (id, name) VALUES (7, 'SENTINEL-NAME-3c9f')" in line, line)
        assertTrue("-> 1 changed (" in line, line)
        assertTrue(line.endsWith(" ms)"), line)
    }

    @Test
    fun aDeleteLogsTheNumberOfChangedRows() {
        driver.insert(1, "a")
        driver.insert(2, "b")
        sink.entries.clear()

        val changed = driver.execute(null, DELETE_ALL, 0).value

        assertEquals(2L, changed)
        val line = sqlMessages().single { DELETE_ALL in it }
        assertTrue(line.startsWith("SQL  WRITE  log_probe"), line)
        assertTrue("-> 2 changed (" in line, line)
    }

    @Test
    fun aSelectLogsTheSqlAndTheRowsRead() {
        driver.insert(1, "a")
        driver.insert(2, "b")
        driver.insert(3, "c")
        sink.entries.clear()

        val names = driver.names()

        assertEquals(listOf("a", "b", "c"), names)
        val line = sqlMessages().single { SELECT in it }
        assertTrue(line.startsWith("SQL  READ   log_probe"), line)
        assertTrue("-> 3 rows (" in line, line)
        assertTrue(line.endsWith(" ms)"), line)
    }

    @Test
    fun aSelectWithoutRowsLogsZeroRows() {
        driver.names()

        assertTrue("-> 0 rows (" in sqlMessages().single { SELECT in it })
    }

    @Test
    fun aTransactionLogsBeginAndCommitAroundItsStatements() {
        Probe(driver).transaction {
            driver.insert(1, "a")
        }

        val messages = sqlMessages()
        val begin = messages.indexOfFirst { it.startsWith("SQL  TX #") && "begin" in it }
        val insert = messages.indexOfFirst { "INSERT INTO log_probe" in it }
        val commit = messages.indexOfFirst { it.startsWith("SQL  TX #") && "commit" in it }
        assertTrue(begin >= 0 && insert > begin && commit > insert, messages.toString())
        assertTrue("1 statements" in messages[commit] || "1 statement" in messages[commit], messages[commit])
        assertTrue(Regex("""\d+ ms\)""").containsMatchIn(messages[commit]), messages[commit])
        assertTrue(messages.none { "rollback" in it }, messages.toString())
    }

    @Test
    fun aTransactionThatThrowsLogsRollbackAndRethrows() {
        val failure = IllegalStateException("boom")

        val thrown =
            assertFailsWith<IllegalStateException> {
                Probe(driver).transaction {
                    driver.insert(1, "a")
                    throw failure
                }
            }

        assertEquals(failure, thrown)
        val messages = sqlMessages()
        assertTrue(messages.any { it.startsWith("SQL  TX #") && "begin" in it }, messages.toString())
        assertTrue(messages.any { it.startsWith("SQL  TX #") && "rollback" in it }, messages.toString())
        assertTrue(messages.none { "commit" in it }, messages.toString())
        assertEquals(emptyList(), driver.names())
    }

    @Test
    fun aRolledBackTransactionLeavesNoRows() {
        Probe(driver).transaction {
            driver.insert(1, "a")
            rollback()
        }

        assertTrue(sqlMessages().any { it.startsWith("SQL  TX #") && "rollback" in it }, sqlMessages().toString())
        assertEquals(emptyList(), driver.names())
    }

    @Test
    fun notifyingListenersLogsTheKeys() {
        driver.notifyListeners("list_entry", "anime")

        val line = sqlMessages().single { it.startsWith("SQL  NOTIFY") }
        assertTrue("list_entry" in line && "anime" in line, line)
    }

    @Test
    fun listenersStillReceiveTheNotification() {
        var notified = 0
        val listener = Query.Listener { notified++ }
        driver.addListener("list_entry", listener = listener)

        driver.notifyListeners("list_entry")
        driver.removeListener("list_entry", listener = listener)
        driver.notifyListeners("list_entry")

        assertEquals(1, notified)
    }

    @Test
    fun aFailingStatementIsRethrownUnchangedAndLogged() {
        val expected = assertFailsWith<Exception> { raw.execute(null, BAD_SQL, 0).value }

        val actual = assertFailsWith<Exception> { driver.execute(null, BAD_SQL, 0).value }

        assertEquals(expected::class, actual::class)
        assertEquals(expected.message, actual.message)
        val line = sqlMessages().single { BAD_SQL in it }
        assertTrue("-> failed: ${expected::class.simpleName} (" in line, line)
    }

    @Test
    fun aFailingQueryIsRethrownUnchanged() {
        val expected =
            assertFailsWith<Exception> { raw.executeQuery(null, BAD_SQL, { QueryResult.Value(Unit) }, 0).value }

        val actual =
            assertFailsWith<Exception> { driver.executeQuery(null, BAD_SQL, { QueryResult.Value(Unit) }, 0).value }

        assertEquals(expected::class, actual::class)
        assertEquals(expected.message, actual.message)
        val line = sqlMessages().single { BAD_SQL in it }
        assertTrue("-> failed: ${expected::class.simpleName} (" in line, line)
    }

    @Test
    fun resultsAreIdenticalToTheUnwrappedDriver() {
        listOf(raw, driver).forEach { target ->
            target.insert(2, "b")
            target.insert(1, "a")
        }

        assertEquals(raw.names(), driver.names())
        assertEquals(
            raw.execute(null, DELETE_ALL, 0).value,
            driver.execute(null, DELETE_ALL, 0).value,
        )
        assertEquals(raw.names(), driver.names())
    }

    @Test
    fun aDebugSinkReceivesNoSqlEntriesAndTheLoggerIsNotLoggableAtTrace() {
        val debugSink = RecordingLogSink(minLevel = LogLevel.Debug)
        val debugLogger = SinkLogger(listOf(debugSink))
        val quiet = LoggingSqlDriver(createTestDriver(), debugLogger)

        quiet.execute(null, CREATE, 0).value
        quiet.insert(1, "SENTINEL-NAME-3c9f")
        quiet.names()
        Probe(quiet).transaction { quiet.insert(2, "b") }
        quiet.notifyListeners("list_entry")

        assertFalse(debugLogger.isLoggable(LogLevel.Trace))
        assertTrue(debugSink.entries.none { it.tag == SQL_TAG }, debugSink.entries.toString())
        assertEquals(listOf("SENTINEL-NAME-3c9f", "b"), quiet.names())
    }

    @Test
    fun aNoOpLoggerNeverFailsTheDriver() {
        val quiet = LoggingSqlDriver(createTestDriver(), NoOpAppLogger)

        quiet.execute(null, CREATE, 0).value
        quiet.insert(1, "a")

        assertEquals(listOf("a"), quiet.names())
    }
}
