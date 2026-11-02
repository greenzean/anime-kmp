package com.greenzean.animekmp.shared.core.logging

object LogRedactor {
    const val MASK = "****"
    const val DEFAULT_MAX_LENGTH = 4096

    private val secretKeys =
        listOf(
            "access_token",
            "refresh_token",
            "id_token",
            "accessToken",
            "refreshToken",
            "idToken",
            "code",
            "code_verifier",
            "code_challenge",
            "state",
            "client_secret",
        )
    private val secretKeysLower = secretKeys.map { it.lowercase() }.toSet()
    private val secretHeaders = setOf("authorization", "cookie", "set-cookie", "proxy-authorization")
    private val keyAlternatives = secretKeys.joinToString("|") { Regex.escape(it) }
    private val jsonPair =
        Regex("\"($keyAlternatives)\"(\\s*:\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"|[^,}\\]\\s]+)", RegexOption.IGNORE_CASE)
    private val formOptions = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    private val formPair = Regex("(^|[?&\\s,;{(])($keyAlternatives)=([^&\\s\"',;]*)", formOptions)
    private val bearer = Regex("Bearer\\s+[A-Za-z0-9._~+/=\\-]+", RegexOption.IGNORE_CASE)
    private val bearerPrefix = Regex("^Bearer\\s", RegexOption.IGNORE_CASE)

    fun redactText(text: String): String {
        val json =
            jsonPair.replace(text) { match ->
                val quoted = match.groupValues[3].startsWith("\"")
                val value = if (quoted) "\"$MASK\"" else MASK
                "\"${match.groupValues[1]}\"${match.groupValues[2]}$value"
            }
        val form = formPair.replace(json) { match -> "${match.groupValues[1]}${match.groupValues[2]}=$MASK" }
        return bearer.replace(form, "Bearer $MASK")
    }

    fun redactUrl(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val fragmentStart = url.indexOf('#', queryStart).let { if (it < 0) url.length else it }
        val query =
            url.substring(queryStart + 1, fragmentStart).split('&').joinToString("&") { pair ->
                val key = pair.substringBefore('=')
                if (pair.contains('=') && key.lowercase() in secretKeysLower) "$key=$MASK" else pair
            }
        return url.substring(0, queryStart + 1) + query + url.substring(fragmentStart)
    }

    fun redactHeader(
        name: String,
        value: String,
    ): String =
        when {
            name.lowercase() !in secretHeaders -> redactText(value)
            bearerPrefix.containsMatchIn(value) -> "Bearer $MASK"
            else -> MASK
        }

    fun truncate(
        text: String,
        max: Int = DEFAULT_MAX_LENGTH,
    ): String = if (text.length <= max) text else text.take(max) + "... (${text.length - max} more chars)"
}
