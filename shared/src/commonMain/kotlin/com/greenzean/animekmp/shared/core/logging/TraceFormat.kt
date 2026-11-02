package com.greenzean.animekmp.shared.core.logging

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

const val API_TAG = "API"
const val SQL_TAG = "SQL"

@OptIn(ExperimentalAtomicApi::class)
object TraceIds {
    private val counter = AtomicInt(0)

    fun next(): Int = counter.incrementAndFetch()
}

data class ApiCall(
    val id: Int,
    val source: String,
    val title: String,
    val outcome: String,
    val durationMs: Long,
    val url: String?,
    val requestHeaders: List<Pair<String, String>>,
    val requestBody: String?,
    val responseHeaders: List<Pair<String, String>>?,
    val responseBody: String?,
)

object TraceFormatter {
    fun apiCall(call: ApiCall): String = formatApiCall(call)

    fun prettyBody(
        body: String,
        max: Int = 8192,
    ): String = prettyBodyText(body, max)

    fun usefulResponseHeaders(headers: List<Pair<String, String>>): List<Pair<String, String>> =
        headers.filter { (name, _) -> isUsefulResponseHeader(name) }
}

enum class SqlKind { Read, Write }

data class SqlText(
    val kind: SqlKind,
    val table: String?,
    val text: String,
)

object SqlFormatter {
    fun format(
        sql: String,
        values: List<Any?>,
    ): SqlText = formatSql(sql, values)
}
