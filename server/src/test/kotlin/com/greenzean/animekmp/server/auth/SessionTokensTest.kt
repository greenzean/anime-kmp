package com.greenzean.animekmp.server.auth

import com.auth0.jwt.JWT
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val SECRET = "0123456789abcdef0123456789abcdef"

class SessionTokensTest {
    private val tokens = SessionTokens(SECRET, TEST_CLOCK)
    private val user = UUID.randomUUID()

    @Test
    fun accessTokenRoundTripsAndExpiresInFifteenMinutes() {
        val issued = tokens.issueAccess(user)
        assertEquals(TEST_NOW.plus(ACCESS_TOKEN_TTL), issued.expiresAt)
        assertEquals(user, tokens.verifyAccess(issued.token))
        assertEquals("anime-kmp", JWT.decode(issued.token).issuer)
    }

    @Test
    fun expiredAccessTokenIsRejected() {
        val issued = tokens.issueAccess(user)
        val later = SessionTokens(SECRET, Clock.fixed(TEST_NOW.plus(ACCESS_TOKEN_TTL).plusSeconds(1), ZoneOffset.UTC))
        assertFailsWith<AuthFailure> { later.verifyAccess(issued.token) }
    }

    @Test
    fun tokenSignedWithAnotherSecretIsRejected() {
        val forged = SessionTokens("another-secret-another-secret-123", TEST_CLOCK).issueAccess(user)
        assertFailsWith<AuthFailure> { tokens.verifyAccess(forged.token) }
    }

    @Test
    fun tamperedAccessTokenIsRejected() {
        val parts = tokens.issueAccess(user).token.split(".")
        assertFailsWith<AuthFailure> { tokens.verifyAccess("${parts[0]}.${parts[1]}.${parts[2].reversed()}") }
    }

    @Test
    fun googleIdTokenIsNotAnAccessToken() {
        val (_, private) = newRsaKey()
        assertFailsWith<AuthFailure> { tokens.verifyAccess(googleIdToken(private)) }
    }

    @Test
    fun garbageIsRejected() {
        assertFailsWith<AuthFailure> { tokens.verifyAccess("nope") }
    }

    @Test
    fun refreshTokensAreRandomUrlSafeAndHashStably() {
        val a = tokens.newRefreshToken()
        val b = tokens.newRefreshToken()
        assertNotEquals(a.token, b.token)
        assertEquals(SessionTokens.REFRESH_TOKEN_LENGTH, a.token.length)
        assertTrue(a.token.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(TEST_NOW.plus(REFRESH_TOKEN_TTL), a.expiresAt)
        assertTrue(SessionTokens.hash(a.token).contentEquals(SessionTokens.hash(a.token)))
        assertEquals(32, SessionTokens.hash(a.token).size)
    }
}
