package com.greenzean.animekmp.server.auth

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.RSAKeyProvider
import java.net.URI
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.util.concurrent.TimeUnit

private const val GOOGLE_CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs"
private const val LEEWAY_SECONDS = 30L
private const val KEY_CACHE_SIZE = 10L
private const val KEY_CACHE_HOURS = 6L
private const val KEY_FETCHES_PER_MINUTE = 10L

private val GOOGLE_ISSUERS = arrayOf("accounts.google.com", "https://accounts.google.com")

/** What the app trusts from a verified Google ID token. `sub` is Google's stable user id; email is never read. */
data class GoogleIdentity(
    val sub: String,
    val nonce: String,
)

/**
 * Verifies a Google ID token: RS256 signature against Google's rotating keys, `iss`, `aud` (our web client ID),
 * `exp`/`iat`, and requires `sub` and `nonce`. Whether the nonce was issued by us is checked by the caller.
 */
class GoogleIdTokenVerifier(
    clientId: String,
    keys: RSAKeyProvider,
    clock: Clock = Clock.systemUTC(),
) {
    // Only RS256: `alg=none` and HS256 tokens fail the algorithm match.
    private val verifier =
        JWT
            .require(Algorithm.RSA256(keys))
            .withIssuer(*GOOGLE_ISSUERS)
            .withAudience(clientId)
            .acceptLeeway(LEEWAY_SECONDS)
            .let { (it as JWTVerifier.BaseVerification).build(clock) }

    fun verify(idToken: String): GoogleIdentity {
        val jwt = decode(idToken)
        // java-jwt only checks `exp` when present; a token that never expires is not a Google ID token.
        if (jwt.expiresAt == null) throw AuthFailure("Google ID token has no exp")
        return GoogleIdentity(
            sub = requiredClaim(jwt.subject, "sub"),
            nonce = requiredClaim(jwt.getClaim("nonce").asString(), "nonce"),
        )
    }

    private fun decode(idToken: String): DecodedJWT =
        try {
            verifier.verify(idToken)
        } catch (e: JWTVerificationException) {
            throw AuthFailure("Google ID token rejected: ${e.javaClass.simpleName}: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw AuthFailure("Google ID token rejected: ${e.message}", e)
        }

    private fun requiredClaim(
        value: String?,
        name: String,
    ): String = value?.takeIf { it.isNotEmpty() } ?: throw AuthFailure("Google ID token has no $name")

    companion object {
        /** Key provider backed by Google's JWKS endpoint: cached, rate limited, refetches on an unknown `kid`. */
        fun googleKeys(): RSAKeyProvider {
            val jwks =
                JwkProviderBuilder(URI(GOOGLE_CERTS_URL).toURL())
                    .cached(KEY_CACHE_SIZE, KEY_CACHE_HOURS, TimeUnit.HOURS)
                    .rateLimited(KEY_FETCHES_PER_MINUTE, 1, TimeUnit.MINUTES)
                    .build()
            return object : RSAKeyProvider {
                override fun getPublicKeyById(keyId: String?): RSAPublicKey? =
                    keyId?.let { jwks.get(it).publicKey as? RSAPublicKey }

                override fun getPrivateKey(): RSAPrivateKey? = null

                override fun getPrivateKeyId(): String? = null
            }
        }
    }
}
