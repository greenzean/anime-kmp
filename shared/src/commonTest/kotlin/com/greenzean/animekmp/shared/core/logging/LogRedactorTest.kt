package com.greenzean.animekmp.shared.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SECRET = "SENTINEL-SECRET-9d41"
private const val MASK = LogRedactor.MASK

private val SECRET_KEYS =
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

class LogRedactorTest {
    @Test
    fun theMaskIsFourStarsAndTheDefaultLimitIs4Kb() {
        assertEquals("****", LogRedactor.MASK)
        assertEquals(4096, LogRedactor.DEFAULT_MAX_LENGTH)
    }

    @Test
    fun everySecretKeyIsMaskedInJsonAndTheKeyIsKept() {
        SECRET_KEYS.forEach { key ->
            val result = LogRedactor.redactText("""{"title":"Bebop","$key":"$SECRET","limit":20}""")

            assertFalse(SECRET in result, "$key leaked: $result")
            assertTrue("\"$key\"" in result, "$key lost: $result")
            assertTrue(MASK in result, result)
            assertTrue("\"title\":\"Bebop\"" in result, result)
            assertTrue("\"limit\":20" in result, result)
        }
    }

    @Test
    fun everySecretKeyIsMaskedInJsonWithSpacesAroundTheColon() {
        SECRET_KEYS.forEach { key ->
            val result = LogRedactor.redactText("""{ "$key" : "$SECRET" }""")

            assertFalse(SECRET in result, "$key leaked: $result")
            assertTrue("\"$key\"" in result, result)
        }
    }

    @Test
    fun everySecretKeyIsMaskedInFormBodies() {
        SECRET_KEYS.forEach { key ->
            val result = LogRedactor.redactText("client_id=client-1&$key=$SECRET&grant_type=refresh")

            assertFalse(SECRET in result, "$key leaked: $result")
            assertTrue("$key=$MASK" in result, result)
            assertTrue("client_id=client-1" in result, result)
            assertTrue("grant_type=refresh" in result, result)
        }
    }

    @Test
    fun aSecretAtTheEndOfAFormBodyIsMasked() {
        val result = LogRedactor.redactText("grant_type=refresh_token&refresh_token=$SECRET")

        assertEquals("grant_type=refresh_token&refresh_token=$MASK", result)
    }

    @Test
    fun everySecretKeyIsMaskedInUrlQueries() {
        SECRET_KEYS.forEach { key ->
            val result = LogRedactor.redactUrl("https://api.example.test/v2/anime?q=bebop&$key=$SECRET&limit=20")

            assertFalse(SECRET in result, "$key leaked: $result")
            assertTrue("$key=$MASK" in result, result)
            assertTrue("https://api.example.test/v2/anime?q=bebop" in result, result)
            assertTrue("limit=20" in result, result)
        }
    }

    @Test
    fun aSecretAsTheFirstQueryParameterIsMasked() {
        val result = LogRedactor.redactUrl("https://auth.example.test/cb?code=$SECRET&other=1")

        assertEquals("https://auth.example.test/cb?code=$MASK&other=1", result)
    }

    @Test
    fun aUrlWithoutSecretsIsUntouched() {
        val url = "https://api.myanimelist.net/v2/anime?q=cowboy+bebop&limit=20&offset=0&fields=id,title&nsfw=false"

        assertEquals(url, LogRedactor.redactUrl(url))
    }

    @Test
    fun aUrlWithoutAQueryIsUntouched() {
        val url = "https://api.myanimelist.net/v2/anime/21"

        assertEquals(url, LogRedactor.redactUrl(url))
    }

    @Test
    fun secretKeysAreMatchedCaseInsensitively() {
        val json =
            LogRedactor.redactText(
                """{"ACCESS_TOKEN":"$SECRET","Id_Token":"$SECRET","REFRESHTOKEN":"$SECRET"}""",
            )
        val form = LogRedactor.redactText("Code=$SECRET&CODE_VERIFIER=$SECRET")
        val url = LogRedactor.redactUrl("https://x.test/p?State=$SECRET")

        assertFalse(SECRET in json, json)
        assertFalse(SECRET in form, form)
        assertFalse(SECRET in url, url)
    }

    @Test
    fun bearerTokensAreMasked() {
        val result = LogRedactor.redactText("Authorization: Bearer $SECRET and more")

        assertFalse(SECRET in result, result)
        assertTrue("Bearer $MASK" in result, result)
        assertTrue("and more" in result, result)
    }

    @Test
    fun bearerIsMaskedCaseInsensitively() {
        val result = LogRedactor.redactText("header bearer $SECRET")

        assertFalse(SECRET in result, result)
    }

    @Test
    fun severalSecretsInOneTextAreAllMasked() {
        val text =
            """{"data":{"signIn":{"accessToken":"$SECRET-a","refreshToken":"$SECRET-b"}},"extra":"Bearer $SECRET-c"}"""

        val result = LogRedactor.redactText(text)

        assertFalse("SENTINEL-SECRET" in result, result)
        assertTrue("\"signIn\"" in result, result)
        assertTrue("\"extra\"" in result, result)
    }

    @Test
    fun textWithoutSecretsIsUntouched() {
        val text =
            """{"data":[{"node":{"id":1,"title":"Cowboy Bebop"}}],"paging":{"next":"x"}} q=bebop&limit=20 status=ok"""

        assertEquals(text, LogRedactor.redactText(text))
    }

    @Test
    fun nonSecretKeysKeepTheirValues() {
        val text =
            """{"client_id":"client-1","grant_type":"authorization_code","redirect_uri":"app://cb","name":"user"}"""

        assertEquals(text, LogRedactor.redactText(text))
    }

    @Test
    fun emptyTextIsUntouched() {
        assertEquals("", LogRedactor.redactText(""))
        assertEquals("", LogRedactor.redactUrl(""))
    }

    @Test
    fun redactingTwiceGivesTheSameResult() {
        val text = """{"accessToken":"$SECRET"} code=$SECRET&x=1 Bearer $SECRET"""
        val url = "https://x.test/p?code=$SECRET&q=a"

        val once = LogRedactor.redactText(text)
        val onceUrl = LogRedactor.redactUrl(url)

        assertEquals(once, LogRedactor.redactText(once))
        assertEquals(onceUrl, LogRedactor.redactUrl(onceUrl))
    }

    @Test
    fun authorizationHeaderShowsBearerMasked() {
        assertEquals("Bearer $MASK", LogRedactor.redactHeader("Authorization", "Bearer $SECRET"))
    }

    @Test
    fun authorizationHeaderWithoutBearerIsFullyMasked() {
        assertEquals(MASK, LogRedactor.redactHeader("Authorization", "Basic $SECRET"))
    }

    @Test
    fun cookieAndAuthRelatedHeadersAreFullyMasked() {
        listOf("Cookie", "Set-Cookie", "Proxy-Authorization").forEach { name ->
            val result = LogRedactor.redactHeader(name, "session=$SECRET")

            assertFalse(SECRET in result, "$name leaked: $result")
            assertTrue(MASK in result, result)
        }
    }

    @Test
    fun headerNamesAreMatchedCaseInsensitively() {
        listOf("authorization", "AUTHORIZATION", "cookie", "SET-COOKIE").forEach { name ->
            assertFalse(SECRET in LogRedactor.redactHeader(name, "Bearer $SECRET"), name)
        }
    }

    @Test
    fun otherHeadersKeepTheirValues() {
        assertEquals("client-1", LogRedactor.redactHeader("X-MAL-CLIENT-ID", "client-1"))
        assertEquals("application/json", LogRedactor.redactHeader("Content-Type", "application/json"))
    }

    @Test
    fun truncateLeavesTextAtOrUnderTheLimitUntouched() {
        assertEquals("", LogRedactor.truncate("", 5))
        assertEquals("abcd", LogRedactor.truncate("abcd", 5))
        assertEquals("abcde", LogRedactor.truncate("abcde", 5))
    }

    @Test
    fun truncateCutsOneOverTheLimitAndCountsTheRest() {
        val result = LogRedactor.truncate("abcdef", 5)

        assertTrue(result.startsWith("abcde"), result)
        assertFalse("f" in result.removePrefix("abcde"), result)
        assertTrue(result.endsWith("... (1 more chars)"), result)
    }

    @Test
    fun truncateReportsTheExactNumberOfDroppedChars() {
        val result = LogRedactor.truncate("x".repeat(100), 10)

        assertEquals("x".repeat(10) + "... (90 more chars)", result)
    }

    @Test
    fun truncateUsesFourKbByDefault() {
        val atLimit = "y".repeat(LogRedactor.DEFAULT_MAX_LENGTH)
        val over = "y".repeat(LogRedactor.DEFAULT_MAX_LENGTH + 7)

        assertEquals(atLimit, LogRedactor.truncate(atLimit))
        assertEquals(atLimit + "... (7 more chars)", LogRedactor.truncate(over))
    }
}
