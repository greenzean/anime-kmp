package com.greenzean.animekmp.shared.core.logging

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalAtomicApi::class)
class LoggingSqlDriver(
    private val delegate: SqlDriver,
    private val logger: AppLogger,
) : SqlDriver by delegate {
    private val transactions = SqlTransactionTracker()

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        if (!logger.isLoggable(LogLevel.Trace)) return delegate.execute(identifier, sql, parameters, binders)
        val recorder = BindRecorder()
        val started = TimeSource.Monotonic.markNow()
        return traced(sql, recorder, started) {
            delegate.execute(identifier, sql, parameters, recorder.wrap(binders))
        }.afterValue { changed ->
            val transaction = currentInfo()
            logger.trace(SQL_TAG) {
                statementLine(sql, recorder, transaction?.id, "$changed changed (${started.millis()} ms)")
            }
        }
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        if (!logger.isLoggable(LogLevel.Trace)) {
            return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
        }
        val recorder = BindRecorder()
        val started = TimeSource.Monotonic.markNow()
        val loggingMapper: (SqlCursor) -> QueryResult<R> = { cursor ->
            val counting = CountingSqlCursor(cursor)
            mapper(counting).afterValue {
                val transaction = currentInfo()
                logger.trace(SQL_TAG) {
                    statementLine(
                        sql,
                        recorder,
                        transaction?.id,
                        "${counting.rows} ${if (counting.rows == 1) "row" else "rows"} (${started.millis()} ms)",
                    )
                }
            }
        }
        return traced(sql, recorder, started) {
            delegate.executeQuery(identifier, sql, loggingMapper, parameters, recorder.wrap(binders))
        }
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        if (!logger.isLoggable(LogLevel.Trace)) return delegate.newTransaction()
        val info = SqlTransactionInfo(TraceIds.next())
        logger.trace(SQL_TAG, "SQL  TX #${info.id}  begin")
        return delegate.newTransaction().afterValue { transaction ->
            transactions.begin(transaction, info)
            transaction.afterCommit {
                transactions.end(transaction)
                logger.trace(SQL_TAG) { transactionEnd(info, "commit", withNotified = true) }
            }
            transaction.afterRollback {
                transactions.end(transaction)
                logger.trace(SQL_TAG) { transactionEnd(info, "rollback", withNotified = false) }
            }
        }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        if (logger.isLoggable(LogLevel.Trace)) {
            val owner = transactions.infoFor(delegate.currentTransaction()) ?: transactions.soleActive()
            if (owner != null) {
                transactions.recordNotified(owner, queryKeys.toList())
            } else {
                logger.trace(SQL_TAG, "SQL  NOTIFY  ${queryKeys.joinToString(", ")}")
            }
        }
        delegate.notifyListeners(*queryKeys)
    }

    private fun currentInfo(): SqlTransactionInfo? =
        transactions.infoFor(delegate.currentTransaction())?.also { transactions.recordStatement(it) }

    private fun transactionEnd(
        info: SqlTransactionInfo,
        verb: String,
        withNotified: Boolean,
    ): String {
        val base = "SQL  TX #${info.id}  $verb  (${info.statements.load()} statements, ${info.started.millis()} ms)"
        val tables = info.notified.load()
        return if (withNotified && tables.isNotEmpty()) "$base   notified: ${tables.joinToString(", ")}" else base
    }

    private inline fun <T> traced(
        sql: String,
        recorder: BindRecorder,
        started: TimeMark,
        block: () -> T,
    ): T =
        runCatching(block)
            .onFailure { e ->
                val transaction = currentInfo()
                val outcome = "failed: ${e::class.simpleName} (${started.millis()} ms)"
                logger.trace(SQL_TAG) {
                    statementLine(sql, recorder, transaction?.id, outcome)
                }
            }.getOrThrow()
}
