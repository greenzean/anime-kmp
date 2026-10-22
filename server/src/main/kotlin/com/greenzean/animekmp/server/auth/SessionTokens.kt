package com.greenzean.animekmp.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

private const val ISSUER = "anime-kmp"
private const val AUDIENCE = "anime-kmp-app"
private const val REFRESH_TOKEN_BYTES = 32

val ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(15)
val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(30)

/** Our own session tokens (ADR 0010): a short-lived HS256 access JWT and an opaque random refresh token. */
class SessionTokens(
    secret: String,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) {
    private val algorithm = Algorithm.HMAC256(secret)
    private val verifier =
        JWT
            .require(algorithm)
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .let { (it as JWTVerifier.BaseVerification).build(clock) }

    fun issueAccess(userId: UUID): IssuedToken {
        val now = clock.instant()
        val expiresAt = now.plus(ACCESS_TOKEN_TTL)
        val token =
            JWT
                .create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withSubject(userId.toString())
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm)
        return IssuedToken(token, expiresAt)
    }

    /** The user id of a valid access token. M2-5 calls this to authorize sync. */
    fun verifyAccess(token: String): UUID =
        try {
            UUID.fromString(verifier.verify(token).subject)
        } catch (e: JWTVerificationException) {
            throw AuthFailure("Access token rejected: ${e.javaClass.simpleName}", e)
        } catch (e: IllegalArgumentException) {
            throw AuthFailure("Access token rejected: ${e.javaClass.simpleName}", e)
        }

    fun newRefreshToken(): IssuedToken {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES).also(random::nextBytes)
        return IssuedToken(ENCODER.encodeToString(bytes), clock.instant().plus(REFRESH_TOKEN_TTL))
    }

    companion object {
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()

        /** Length of an encoded refresh token, for cheap input validation. */
        val REFRESH_TOKEN_LENGTH: Int = ENCODER.encodeToString(ByteArray(REFRESH_TOKEN_BYTES)).length

        fun hash(refreshToken: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(refreshToken.toByteArray())
    }
}

data class IssuedToken(
    val token: String,
    val expiresAt: Instant,
)
