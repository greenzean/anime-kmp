package com.greenzean.animekmp.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

const val TEST_CLIENT_ID = "test-client.apps.googleusercontent.com"
const val TEST_KEY_ID = "test-key"
val TEST_NOW: Instant = Instant.parse("2026-10-22T12:00:00Z")
val TEST_CLOCK: Clock = Clock.fixed(TEST_NOW, ZoneOffset.UTC)

fun newRsaKey(): Pair<RSAPublicKey, RSAPrivateKey> {
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    return pair.public as RSAPublicKey to pair.private as RSAPrivateKey
}

/** A key set that only knows `TEST_KEY_ID`, standing in for Google's JWKS. */
class FakeGoogleKeys(
    private val publicKey: RSAPublicKey,
) : RSAKeyProvider {
    override fun getPublicKeyById(keyId: String?): RSAPublicKey? = publicKey.takeIf { keyId == TEST_KEY_ID }

    override fun getPrivateKey(): RSAPrivateKey? = null

    override fun getPrivateKeyId(): String? = null
}

/** Builds a Google-shaped ID token; every claim can be overridden or dropped (null) to break one check. */
fun googleIdToken(
    privateKey: RSAPrivateKey,
    keyId: String? = TEST_KEY_ID,
    issuer: String? = "https://accounts.google.com",
    audience: String? = TEST_CLIENT_ID,
    subject: String? = "google-sub-1",
    nonce: String? = "nonce-1",
    issuedAt: Instant? = TEST_NOW.minusSeconds(60),
    expiresAt: Instant? = TEST_NOW.plusSeconds(3600),
    algorithm: Algorithm = Algorithm.RSA256(null, privateKey),
    email: String? = "someone@example.com",
): String =
    JWT
        .create()
        .apply {
            keyId?.let(::withKeyId)
            issuer?.let(::withIssuer)
            audience?.let(::withAudience)
            subject?.let(::withSubject)
            nonce?.let { withClaim("nonce", it) }
            issuedAt?.let(::withIssuedAt)
            expiresAt?.let(::withExpiresAt)
            email?.let { withClaim("email", it) }
        }.sign(algorithm)
