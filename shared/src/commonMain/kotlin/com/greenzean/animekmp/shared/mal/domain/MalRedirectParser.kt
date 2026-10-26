package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import io.ktor.http.Url

private fun parseUrl(value: String): Url? =
    try {
        Url(value)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

fun parseMalRedirect(uri: String): Result<MalRedirect, MalAuthError> {
    val parameters = parseUrl(uri)?.parameters ?: return Result.Error(MalAuthError.UNKNOWN)
    val code = parameters["code"]
    val state = parameters["state"]
    return when {
        parameters["error"] == "access_denied" -> Result.Error(MalAuthError.ACCESS_DENIED)
        parameters["error"] != null -> Result.Error(MalAuthError.UNKNOWN)
        code.isNullOrBlank() || state.isNullOrBlank() -> Result.Error(MalAuthError.UNKNOWN)
        else -> Result.Success(MalRedirect(code, state))
    }
}

fun isMalRedirect(
    uri: String,
    redirectUri: String,
): Boolean {
    val actual = parseUrl(uri)
    val expected = parseUrl(redirectUri)
    return redirectUri.isNotBlank() &&
        actual != null &&
        expected != null &&
        actual.protocol == expected.protocol &&
        actual.host.equals(expected.host, ignoreCase = true) &&
        actual.port == expected.port &&
        actual.encodedPath.trimEnd('/') == expected.encodedPath.trimEnd('/')
}

fun malRedirectState(uri: String): String? = parseUrl(uri)?.parameters?.get("state")
