package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.core.domain.map
import io.ktor.http.URLBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val EXPIRY_MARGIN = 30.seconds
private const val AUTHORIZE_URL = "https://myanimelist.net/v1/oauth2/authorize"
private const val VERIFIER_BYTES = 64
private const val STATE_BYTES = 16

class MalAuthManager(
    private val config: MalConfig,
    private val api: MalAuthApi,
    private val store: MalTokenStore,
    private val authorizer: MalAuthorizer,
    private val randomBytes: RandomBytes,
    private val clock: Clock = Clock.System,
) {
    private val loginLock = Mutex()
    private val refreshLock = Mutex()
    private var generation = 0

    val isLoggedIn: Flow<Boolean> = store.session.map { it != null }

    suspend fun login(): Result<Unit, MalAuthError> =
        when {
            config.clientId.isBlank() || config.redirectUri.isBlank() -> {
                Result.Error(MalAuthError.NOT_CONFIGURED)
            }

            !loginLock.tryLock() -> {
                Result.Error(MalAuthError.IN_PROGRESS)
            }

            else -> {
                try {
                    authorizeAndExchange()
                } finally {
                    loginLock.unlock()
                }
            }
        }

    private suspend fun authorizeAndExchange(): Result<Unit, MalAuthError> {
        val verifier = encode(randomBytes.next(VERIFIER_BYTES))
        val state = encode(randomBytes.next(STATE_BYTES))
        val startedAt = generation
        return when (val redirect = authorizer.authorize(authorizeUrl(verifier, state), state)) {
            is Result.Error -> Result.Error(redirect.error)
            is Result.Success -> exchange(redirect.data, state, verifier, startedAt)
        }
    }

    suspend fun logout() {
        refreshLock.withLock {
            generation++
            store.clear()
        }
    }

    suspend fun accessToken(): Result<String, MalAuthError> =
        refreshLock.withLock {
            val session = store.get() ?: return@withLock Result.Error(MalAuthError.NOT_LOGGED_IN)
            if (session.accessTokenExpiresAt - EXPIRY_MARGIN > clock.now()) {
                Result.Success(session.accessToken)
            } else {
                renew(session)
            }
        }

    private suspend fun exchange(
        redirect: MalRedirect,
        state: String,
        verifier: String,
        startedAt: Int,
    ): Result<Unit, MalAuthError> {
        if (redirect.state != state) return Result.Error(MalAuthError.STATE_MISMATCH)
        return when (val tokens = api.exchangeCode(redirect.code, verifier)) {
            is Result.Error -> {
                Result.Error(tokens.error)
            }

            is Result.Success -> {
                refreshLock.withLock {
                    if (generation !=
                        startedAt
                    ) {
                        Result.Error(MalAuthError.CANCELLED)
                    } else {
                        store.save(tokens.data.toSession())
                    }
                }
            }
        }
    }

    private suspend fun renew(session: MalSession): Result<String, MalAuthError> =
        when (val tokens = api.refresh(session.refreshToken)) {
            is Result.Success -> {
                val renewed = tokens.data.toSession()
                store.save(renewed).map { renewed.accessToken }
            }

            is Result.Error -> {
                when {
                    tokens.error == MalAuthError.INVALID_GRANT -> {
                        store.clear()
                        Result.Error(tokens.error)
                    }

                    session.accessTokenExpiresAt > clock.now() -> {
                        Result.Success(session.accessToken)
                    }

                    else -> {
                        Result.Error(tokens.error)
                    }
                }
            }
        }

    private fun authorizeUrl(
        verifier: String,
        state: String,
    ): String =
        URLBuilder(AUTHORIZE_URL)
            .apply {
                parameters.append("response_type", "code")
                parameters.append("client_id", config.clientId)
                parameters.append("code_challenge", verifier)
                parameters.append("code_challenge_method", "plain")
                parameters.append("state", state)
                parameters.append("redirect_uri", config.redirectUri)
            }.buildString()

    private fun MalTokens.toSession() =
        MalSession(
            accessToken = accessToken,
            accessTokenExpiresAt = clock.now() + expiresInSeconds.seconds,
            refreshToken = refreshToken,
        )

    private fun encode(bytes: ByteArray): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
}
