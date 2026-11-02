package com.greenzean.animekmp.shared.core.logging

import app.cash.sqldelight.Transacter
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.update
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalAtomicApi::class)
internal class SqlTransactionInfo(
    val id: Int,
) {
    val started: TimeMark = TimeSource.Monotonic.markNow()
    val statements = AtomicInt(0)
    val notified = AtomicReference<List<String>>(emptyList())
}

@OptIn(ExperimentalAtomicApi::class)
internal class SqlTransactionTracker {
    private val active = AtomicReference<List<Pair<Transacter.Transaction, SqlTransactionInfo>>>(emptyList())

    fun begin(
        transaction: Transacter.Transaction,
        info: SqlTransactionInfo,
    ) = active.update { it + (transaction to info) }

    fun end(transaction: Transacter.Transaction) = active.update { list -> list.filterNot { it.first === transaction } }

    fun infoFor(transaction: Transacter.Transaction?): SqlTransactionInfo? =
        transaction?.let { current -> active.load().firstOrNull { it.first === current }?.second }

    fun soleActive(): SqlTransactionInfo? = active.load().singleOrNull()?.second

    fun recordStatement(info: SqlTransactionInfo) = info.statements.incrementAndFetch()

    fun recordNotified(
        info: SqlTransactionInfo,
        tables: List<String>,
    ) = info.notified.update { it + tables }
}
