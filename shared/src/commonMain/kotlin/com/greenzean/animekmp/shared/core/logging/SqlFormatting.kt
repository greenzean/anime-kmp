package com.greenzean.animekmp.shared.core.logging

private const val MAX_RENDERED = 80
private const val CUT_LENGTH = 77
private const val MAX_COLUMNS_TEXT = 60
private const val KB = 1024
private const val TENTHS = 10

private val whitespaceRun = Regex("\\s+")
private val writeStarts = listOf("INSERT", "UPDATE", "DELETE", "REPLACE", "CREATE", "DROP", "ALTER")
private val tableRegex = Regex("\\b(?:FROM|INTO|UPDATE)\\s+[\"`\\[]?([A-Za-z_][A-Za-z0-9_]*)", RegexOption.IGNORE_CASE)
private val selectRegex = Regex("^SELECT\\s+(.*?)\\s+FROM\\s", setOf(RegexOption.IGNORE_CASE))

internal fun formatSql(
    sql: String,
    values: List<Any?>,
): SqlText =
    runCatching {
        val collapsed = whitespaceRun.replace(sql.trim(), " ")
        val upper = collapsed.uppercase()
        val kind = if (writeStarts.any { upper.startsWith(it) }) SqlKind.Write else SqlKind.Read
        val table = tableRegex.find(collapsed)?.groupValues?.get(1)
        SqlText(kind, table, bindValues(abbreviateColumns(collapsed), values))
    }.getOrElse { SqlText(SqlKind.Read, null, sql) }

private fun abbreviateColumns(sql: String): String {
    val match = selectRegex.find(sql)
    val columns = match?.groupValues?.get(1).orEmpty()
    if (match == null || columns.length <= MAX_COLUMNS_TEXT) return sql
    val fromIndex = match.range.last - "FROM ".length + 1
    return "SELECT ${countTopLevelColumns(columns)} columns " + sql.substring(fromIndex)
}

private fun countTopLevelColumns(columns: String): Int {
    var depth = 0
    var commas = 0
    for (ch in columns) {
        when (ch) {
            '(' -> depth++
            ')' -> depth--
            ',' -> if (depth == 0) commas++
        }
    }
    return commas + 1
}

private class Placeholder(
    val start: Int,
    val end: Int,
    val number: Int?,
)

private fun findPlaceholders(sql: String): List<Placeholder> {
    val found = mutableListOf<Placeholder>()
    var i = 0
    var inQuote = false
    while (i < sql.length) {
        val ch = sql[i]
        when {
            ch == '\'' -> {
                inQuote = !inQuote
                i++
            }

            ch == '?' && !inQuote -> {
                var j = i + 1
                while (j < sql.length && sql[j].isDigit()) j++
                found += Placeholder(i, j, sql.substring(i + 1, j).toIntOrNull())
                i = j
            }

            else -> {
                i++
            }
        }
    }
    return found
}

private fun bindValues(
    sql: String,
    values: List<Any?>,
): String {
    val placeholders = findPlaceholders(sql)
    var sequential = 0
    val indexes =
        placeholders.map { it.number?.let { n -> n - 1 } ?: sequential++ }
    val required = (indexes.maxOrNull() ?: -1) + 1
    if (required != values.size || indexes.any { it !in values.indices }) {
        val suffix = if (values.isEmpty()) "" else " [values: ${values.joinToString(", ") { renderValue(it) }}]"
        return sql + suffix
    }
    return buildString {
        var cursor = 0
        placeholders.forEachIndexed { position, placeholder ->
            append(sql, cursor, placeholder.start)
            append(renderValue(values[indexes[position]]))
            cursor = placeholder.end
        }
        append(sql, cursor, sql.length)
    }
}

private fun renderValue(value: Any?): String =
    when (value) {
        null -> "NULL"
        is ByteArray -> "<${value.size} bytes>"
        is String -> renderString(value)
        else -> value.toString()
    }

private fun renderString(value: String): String {
    val escaped = value.replace("'", "''")
    val rendered = "'$escaped'"
    if (rendered.length <= MAX_RENDERED) return rendered
    return "'" + escaped.take(CUT_LENGTH) + "...' (" + sizeText(value.length) + ")"
}

private fun sizeText(chars: Int): String {
    if (chars < KB) return "$chars chars"
    val tenths = chars * TENTHS / KB
    return "${tenths / TENTHS}.${tenths % TENTHS} KB"
}
