package com.greenzean.animekmp.server.auth

import com.auth0.jwt.algorithms.Algorithm
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GoogleIdTokenVerifierTest {
    private val key = newRsaKey()
    private val verifier = GoogleIdTokenVerifier(TEST_CLIENT_ID, FakeGoogleKeys(key.first), TEST_CLOCK)

    private fun token(
        issuer: String? = "https://accounts.google.com",
        audience: String? = TEST_CLIENT_ID,
        subject: String? = "google-sub-1",
        nonce: String? = "nonce-1",
        issuedAt: java.time.Instant? = TEST_NOW.minusSeconds(60),
        expiresAt: java.time.Instant? = TEST_NOW.plusSeconds(3600),
        keyId: String? = TEST_KEY_ID,
    ) = googleIdToken(key.second, keyId, issuer, audience, subject, nonce, issuedAt, expiresAt)

    private fun assertRejected(idToken: String) {
        assertFailsWith<AuthFailure> { verifier.verify(idToken) }
    }

    @Test
    fun validTokenGivesSubAndNonce() {
        assertEquals(GoogleIdentity("google-sub-1", "nonce-1"), verifier.verify(token()))
    }

    @Test
    fun bothGoogleIssuerFormsAreAccepted() {
        verifier.verify(token(issuer = "accounts.google.com"))
        verifier.verify(token(issuer = "https://accounts.google.com"))
    }

    @Test
    fun signatureFromAnotherKeyIsRejected() {
        assertRejected(googleIdToken(newRsaKey().second))
    }

    @Test
    fun unknownKeyIdIsRejected() {
        assertRejected(token(keyId = "rotated-away"))
        assertRejected(token(keyId = null))
    }

    @Test
    fun wrongAudienceIsRejected() {
        assertRejected(token(audience = "someone-elses-client"))
        assertRejected(token(audience = null))
    }

    @Test
    fun wrongIssuerIsRejected() {
        assertRejected(token(issuer = "https://evil.example.com"))
        assertRejected(token(issuer = null))
    }

    @Test
    fun expiredTokenIsRejected() {
        assertRejected(token(expiresAt = TEST_NOW.minusSeconds(120)))
    }

    @Test
    fun missingExpiryIsRejected() {
        assertRejected(token(expiresAt = null))
    }

    @Test
    fun issuedInTheFutureIsRejected() {
        assertRejected(token(issuedAt = TEST_NOW.plusSeconds(3600)))
    }

    @Test
    fun missingSubIsRejected() {
        assertRejected(token(subject = null))
    }

    @Test
    fun missingNonceIsRejected() {
        assertRejected(token(nonce = null))
    }

    @Test
    fun algNoneIsRejected() {
        val unsigned = googleIdToken(key.second, algorithm = Algorithm.none())
        assertRejected(unsigned)
    }

    @Test
    fun hs256SignedWithThePublicKeyIsRejected() {
        val hs = googleIdToken(key.second, algorithm = Algorithm.HMAC256(key.first.encoded))
        assertRejected(hs)
    }

    @Test
    fun tamperedPayloadIsRejected() {
        val parts = token().split(".")
        val payload = String(Base64.getUrlDecoder().decode(parts[1])).replace("google-sub-1", "google-sub-2")
        val forged = "${parts[0]}.${Base64.getUrlEncoder().withoutPadding().encodeToString(
            payload.toByteArray(),
        )}.${parts[2]}"
        assertRejected(forged)
    }

    @Test
    fun garbageIsRejected() {
        assertRejected("not-a-jwt")
        assertRejected("")
    }
}
