package com.greenzean.animekmp.shared.core.logging

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlin.time.TimeMark

internal fun TimeMark.millis(): Long = elapsedNow().inWholeMilliseconds

internal fun statementLine(
    sql: String,
    recorder: BindRecorder,
    transactionId: Int?,
    outcome: String,
): String {
    val text = SqlFormatter.format(sql, recorder.values())
    val label = if (text.kind == SqlKind.Write) "WRITE" else "READ "
    val prefix = if (transactionId != null) "TX #$transactionId  " else ""
    return "SQL  $prefix$label  ${text.table ?: "-"}   ${text.text}   -> $outcome"
}

internal inline fun <T> QueryResult<T>.afterValue(crossinline action: (T) -> Unit): QueryResult<T> =
    when (this) {
        is QueryResult.Value<T> -> {
            action(value)
            this
        }

        is QueryResult.AsyncValue<T> -> {
            val source = this
            QueryResult.AsyncValue { source.await().also { action(it) } }
        }
    }

internal class BindRecorder {
    private val values = mutableMapOf<Int, Any?>()

    fun record(
        index: Int,
        value: Any?,
    ) {
        values[index] = value
    }

    fun wrap(binders: (SqlPreparedStatement.() -> Unit)?): (SqlPreparedStatement.() -> Unit)? {
        if (binders == null) return null
        val wrapped: SqlPreparedStatement.() -> Unit = { RecordingStatement(this, this@BindRecorder).binders() }
        return wrapped
    }

    fun values(): List<Any?> = values.entries.sortedBy { it.key }.map { it.value }
}

internal class RecordingStatement(
    private val target: SqlPreparedStatement,
    private val recorder: BindRecorder,
) : SqlPreparedStatement {
    override fun bindBytes(
        index: Int,
        bytes: ByteArray?,
    ) {
        recorder.record(index, bytes)
        target.bindBytes(index, bytes)
    }

    override fun bindLong(
        index: Int,
        long: Long?,
    ) {
        recorder.record(index, long)
        target.bindLong(index, long)
    }

    override fun bindDouble(
        index: Int,
        double: Double?,
    ) {
        recorder.record(index, double)
        target.bindDouble(index, double)
    }

    override fun bindString(
        index: Int,
        string: String?,
    ) {
        recorder.record(index, string)
        target.bindString(index, string)
    }

    override fun bindBoolean(
        index: Int,
        boolean: Boolean?,
    ) {
        recorder.record(index, boolean)
        target.bindBoolean(index, boolean)
    }
}

internal class CountingSqlCursor(
    private val delegate: SqlCursor,
) : SqlCursor by delegate {
    var rows = 0
        private set

    override fun next(): QueryResult<Boolean> =
        delegate.next().afterValue { hasRow ->
            if (hasRow) rows++
        }
}
