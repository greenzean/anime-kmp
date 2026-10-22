package com.greenzean.animekmp.server.graphql.auth

import com.expediagroup.graphql.server.operations.Mutation
import com.greenzean.animekmp.server.auth.AuthService
import com.greenzean.animekmp.server.auth.Session
import com.greenzean.animekmp.server.auth.SessionTokens

private const val MAX_ID_TOKEN_LENGTH = 4096

/** `auth` is null when GOOGLE_CLIENT_ID and JWT_SECRET are not configured (dev only). */
class AuthMutation(
    private val auth: AuthService? = null,
) : Mutation {
    /** A single-use nonce the app passes to Google when it requests the ID token. */
    fun signInNonce(): String = service().signInNonce()

    fun signInWithGoogle(idToken: String): AuthPayload {
        require(idToken.length <= MAX_ID_TOKEN_LENGTH) { "idToken is too long" }
        return service().signInWithGoogle(idToken).toGraphQL()
    }

    fun refreshSession(refreshToken: String): AuthPayload {
        require(refreshToken.length == SessionTokens.REFRESH_TOKEN_LENGTH) { "Invalid refresh token" }
        return service().refreshSession(refreshToken).toGraphQL()
    }

    private fun service(): AuthService = auth ?: error("Sign-in is not configured")
}

private fun Session.toGraphQL() = AuthPayload(accessToken, accessTokenExpiresAt, refreshToken, refreshTokenExpiresAt)
