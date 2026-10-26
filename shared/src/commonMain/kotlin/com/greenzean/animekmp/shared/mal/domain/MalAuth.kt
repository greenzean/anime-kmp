package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Error
import com.greenzean.animekmp.shared.core.domain.Result
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

data class MalConfig(
    val clientId: String,
    val redirectUri: String,
)

data class MalSession(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
)

data class MalTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

data class MalRedirect(
    val code: String,
    val state: String,
)

enum class MalAuthError : Error {
    NOT_CONFIGURED,
    NOT_LOGGED_IN,
    IN_PROGRESS,
    CANCELLED,
    ACCESS_DENIED,
    STATE_MISMATCH,
    INVALID_GRANT,
    NO_BROWSER,
    NO_INTERNET,
    STORAGE_FAILED,
    RATE_LIMITED,
    SERVER,
    UNKNOWN,
}

interface MalTokenStore {
    val session: Flow<MalSession?>

    suspend fun get(): MalSession?

    suspend fun save(session: MalSession): EmptyResult<MalAuthError>

    suspend fun clear()
}

fun interface MalAuthorizer {
    suspend fun authorize(
        url: String,
        state: String,
    ): Result<MalRedirect, MalAuthError>
}

fun interface RandomBytes {
    fun next(size: Int): ByteArray
}

interface MalAuthApi {
    suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
    ): Result<MalTokens, MalAuthError>

    suspend fun refresh(refreshToken: String): Result<MalTokens, MalAuthError>
}
