package com.greenzean.animekmp.shared.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val F_ACCESS = "SENTINEL-ACCESS-f1a1"
private const val F_REFRESH = "SENTINEL-REFRESH-f2b2"
private const val F_CODE = "SENTINEL-CODE-f3c3"

class TraceFormatterTest {
    private fun call(
        id: Int = 4,
        source: String = "MAL",
        title: String = "GET /v2/anime",
        outcome: String = "200",
        durationMs: Long = 123,
        url: String? = "https://api.myanimelist.net/v2/anime?q=naruto&limit=20",
        requestHeaders: List<Pair<String, String>> = listOf("X-MAL-CLIENT-ID" to "client-1"),
        requestBody: String? = null,
        responseHeaders: List<Pair<String, String>>? = listOf("Content-Type" to "application/json"),
        responseBody: String? = """{"data":[{"id":1}]}""",
    ) = ApiCall(id, source, title, outcome, durationMs, url, requestHeaders, requestBody, responseHeaders, responseBody)

    @Test
    fun theFirstLineHasTheIdSourceTitleOutcomeAndDuration() {
        val text = TraceFormatter.apiCall(call())

        assertEquals("API #4  MAL  GET /v2/anime  ->  200  (123 ms)", text.lines().first())
    }

    @Test
    fun theUrlLineShowsTheFullUrlWithItsQuery() {
        val text = TraceFormatter.apiCall(call())

        assertTrue("  url:      https://api.myanimelist.net/v2/anime?q=naruto&limit=20" in text, text)
    }

    @Test
    fun aGraphQlCallWithoutUrlHasNoUrlLine() {
        val text = TraceFormatter.apiCall(call(source = "GraphQL", title = "query ChangesSince", url = null))

        assertFalse("url:" in text, text)
        assertTrue(text.startsWith("API #4  GraphQL  query ChangesSince  ->  200"), text)
    }

    @Test
    fun requestHeadersAreListedWithTheFirstOnTheRequestLine() {
        val text =
            TraceFormatter.apiCall(
                call(requestHeaders = listOf("X-MAL-CLIENT-ID" to "client-1", "Accept" to "application/json")),
            )

        assertTrue("  request:  X-MAL-CLIENT-ID: client-1" in text, text)
        assertTrue("Accept: application/json" in text, text)
        assertTrue(text.indexOf("X-MAL-CLIENT-ID") < text.indexOf("Accept: application/json"), text)
    }

    @Test
    fun theRequestBodyIsShownUnderItsOwnHeading() {
        val text = TraceFormatter.apiCall(call(requestBody = "grant_type=authorization_code&client_id=client-1"))

        assertTrue("  request body:" in text, text)
        assertTrue("grant_type=authorization_code" in text, text)
        assertTrue("client_id=client-1" in text, text)
    }

    @Test
    fun noRequestBodyHeadingWithoutARequestBody() {
        assertFalse("request body:" in TraceFormatter.apiCall(call(requestBody = null)))
    }

    @Test
    fun theResponsePartShowsUsefulHeadersAndThePrettyBody() {
        val text =
            TraceFormatter.apiCall(
                call(
                    responseHeaders = listOf("Content-Type" to "application/json", "Server" to "nginx"),
                    responseBody = """{"id":1,"name":"x"}""",
                ),
            )

        assertTrue("  response: Content-Type: application/json" in text, text)
        assertFalse("nginx" in text, text)
        assertTrue("\"id\": 1" in text, text)
        assertTrue("\"name\": \"x\"" in text, text)
    }

    @Test
    fun aFailureWithoutAResponseHasNoResponsePart() {
        val text =
            TraceFormatter.apiCall(
                call(outcome = "failed: ConnectTimeoutException", responseHeaders = null, responseBody = null),
            )

        assertTrue("->  failed: ConnectTimeoutException" in text, text)
        assertFalse("response:" in text, text)
    }

    @Test
    fun headerSecretsAreMasked() {
        val text =
            TraceFormatter.apiCall(
                call(requestHeaders = listOf("Authorization" to "Bearer $F_ACCESS", "Cookie" to F_REFRESH)),
            )

        assertTrue("Authorization: Bearer ****" in text, text)
        assertFalse(F_ACCESS in text, text)
        assertFalse(F_REFRESH in text, text)
    }

    @Test
    fun requestFormSecretsAreMasked() {
        val text =
            TraceFormatter.apiCall(
                call(requestBody = "grant_type=authorization_code&code=$F_CODE&refresh_token=$F_REFRESH&client_id=c"),
            )

        assertTrue("code=****" in text, text)
        assertTrue("refresh_token=****" in text, text)
        assertTrue("client_id=c" in text, text)
        assertFalse(F_CODE in text, text)
        assertFalse(F_REFRESH in text, text)
    }

    @Test
    fun responseJsonSecretsAreMasked() {
        val text =
            TraceFormatter.apiCall(
                call(
                    responseBody = """{"access_token":"$F_ACCESS","refresh_token":"$F_REFRESH","expires_in":3600}""",
                ),
            )

        assertTrue("\"access_token\"" in text, text)
        assertTrue("\"expires_in\": 3600" in text, text)
        assertFalse(F_ACCESS in text, text)
        assertFalse(F_REFRESH in text, text)
    }

    @Test
    fun secretsInAGraphQlBodyWithoutUrlAreMasked() {
        val text =
            TraceFormatter.apiCall(
                call(
                    url = null,
                    requestBody = """{"variables":{"idToken":"$F_CODE"}}""",
                    responseBody = """{"data":{"accessToken":"$F_ACCESS","refreshToken":"$F_REFRESH"}}""",
                ),
            )

        listOf(F_CODE, F_ACCESS, F_REFRESH).forEach { assertFalse(it in text, "leaked $it: $text") }
    }

    @Test
    fun prettyBodyIndentsJsonWithTwoSpaces() {
        val text = TraceFormatter.prettyBody("""{"a":1,"b":{"c":[1,2]}}""")

        assertTrue("\n  \"a\": 1" in text, text)
        assertTrue("\n  \"b\": {" in text, text)
        assertTrue("\n    \"c\": [" in text, text)
    }

    @Test
    fun prettyBodySplitsAFormBodyIntoOneLinePerPair() {
        val text = TraceFormatter.prettyBody("a=b&c=d")

        assertEquals(listOf("a=b", "c=d"), text.lines().map { it.trim() }.filter { it.isNotEmpty() })
    }

    @Test
    fun prettyBodyLeavesPlainTextUnchanged() {
        assertEquals("just some text", TraceFormatter.prettyBody("just some text"))
    }

    @Test
    fun prettyBodyDoesNotThrowOnBrokenJson() {
        val text = TraceFormatter.prettyBody("""{"a":1,""")

        assertTrue("\"a\"" in text, text)
    }

    @Test
    fun prettyBodyHandlesAnEmptyBody() {
        assertEquals("", TraceFormatter.prettyBody("").trim())
    }

    @Test
    fun prettyBodyMasksSecretsAfterPrettyPrinting() {
        val json = TraceFormatter.prettyBody("""{"access_token":"$F_ACCESS","n":1}""")
        val form = TraceFormatter.prettyBody("code=$F_CODE&x=1")

        assertFalse(F_ACCESS in json, json)
        assertTrue("****" in json, json)
        assertFalse(F_CODE in form, form)
        assertTrue("code=****" in form, form)
    }

    @Test
    fun prettyBodyTruncatesAtEightKb() {
        val body = "p".repeat(10_000)

        val text = TraceFormatter.prettyBody(body)

        assertTrue(text.startsWith("p".repeat(8192)), text.take(50))
        assertFalse("p".repeat(8193) in text)
        assertTrue(text.endsWith("... (1808 more chars)"), text.takeLast(60))
    }

    @Test
    fun prettyBodyHonoursACustomMax() {
        val text = TraceFormatter.prettyBody("abcdefghij", max = 4)

        assertEquals("abcd... (6 more chars)", text)
    }

    @Test
    fun aBodyWithinTheLimitIsNotTruncated() {
        assertFalse("more chars" in TraceFormatter.prettyBody("p".repeat(8192)))
    }

    @Test
    fun aLongResponseBodyIsTruncatedInTheCallText() {
        val text = TraceFormatter.apiCall(call(responseBody = "p".repeat(10_000)))

        assertFalse("p".repeat(8193) in text)
        assertTrue("more chars)" in text, text.takeLast(80))
    }

    @Test
    fun usefulResponseHeadersKeepOnlyTheInterestingOnesInOrderAndCasing() {
        val headers =
            listOf(
                "Server" to "nginx",
                "Content-Type" to "application/json",
                "X-Powered-By" to "x",
                "content-length" to "12",
                "Retry-After" to "30",
                "LOCATION" to "/next",
                "WWW-Authenticate" to "Bearer",
                "X-RateLimit-Remaining" to "9",
                "Set-Cookie" to "a=b",
            )

        val kept = TraceFormatter.usefulResponseHeaders(headers)

        assertEquals(
            listOf(
                "Content-Type" to "application/json",
                "content-length" to "12",
                "Retry-After" to "30",
                "LOCATION" to "/next",
                "WWW-Authenticate" to "Bearer",
                "X-RateLimit-Remaining" to "9",
            ),
            kept,
        )
    }

    @Test
    fun usefulResponseHeadersOfNothingIsEmpty() {
        assertEquals(emptyList(), TraceFormatter.usefulResponseHeaders(listOf("Server" to "nginx")))
    }

    @Test
    fun traceIdsIncrease() {
        val first = TraceIds.next()
        val second = TraceIds.next()
        val third = TraceIds.next()

        assertTrue(first < second && second < third, "$first $second $third")
    }
}
