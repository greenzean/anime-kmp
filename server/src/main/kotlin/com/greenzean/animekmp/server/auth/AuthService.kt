package com.greenzean.animekmp.server.auth

import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

data class Session(
    val userId: UUID,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
)

private val log = LoggerFactory.getLogger(AuthService::class.java)

/** Sign-in flow (ADR 0010): Google ID token plus a server-issued nonce in, our own session out. */
class AuthService(
    private val verifier: GoogleIdTokenVerifier,
    private val tokens: SessionTokens,
    private val repository: AuthRepository,
) {
    fun signInNonce(): String = repository.issueNonce()

    /** The token is verified first, so a caller without a valid Google token cannot burn nonces. */
    fun signInWithGoogle(idToken: String): Session =
        failing {
            val identity = verifier.verify(idToken)
            if (!repository.consumeNonce(identity.nonce)) throw AuthFailure("Sign-in nonce unknown, expired or used")
            val userId = repository.userIdForSub(identity.sub)
            val refresh = tokens.newRefreshToken()
            repository.storeRefreshToken(
                userId,
                UUID.randomUUID(),
                SessionTokens.hash(refresh.token),
                refresh.expiresAt,
            )
            session(userId, refresh)
        }

    fun refreshSession(refreshToken: String): Session =
        failing {
            val successor = tokens.newRefreshToken()
            when (
                val result =
                    repository.rotate(
                        SessionTokens.hash(refreshToken),
                        SessionTokens.hash(successor.token),
                        successor.expiresAt,
                    )
            ) {
                is RotationResult.Rotated -> session(result.userId, successor)
                RotationResult.Invalid -> throw AuthFailure("Refresh token unknown or expired")
                RotationResult.ReuseDetected -> throw AuthFailure("Refresh token reuse detected, family revoked")
            }
        }

    private fun session(
        userId: UUID,
        refresh: IssuedToken,
    ): Session {
        val access = tokens.issueAccess(userId)
        return Session(userId, access.token, access.expiresAt, refresh.token, refresh.expiresAt)
    }

    private fun <T> failing(block: () -> T): T =
        try {
            block()
        } catch (e: AuthFailure) {
            log.info("Auth rejected: {}", e.reason)
            throw e
        }
}
