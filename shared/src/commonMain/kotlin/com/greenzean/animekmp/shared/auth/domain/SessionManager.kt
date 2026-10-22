package com.greenzean.animekmp.shared.auth.domain

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Our own session (ADR 0010): a short-lived access token and the rotating refresh token that renews it. */
data class Session(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
)

/** Where the session lives. M2-6 adds a persistent, encrypted implementation; until then it is in memory. */
interface SessionStore {
    suspend fun get(): Session?

    suspend fun save(session: Session)

    suspend fun clear()
}

/** Exchanges a refresh token for a new session. [DataError.Network.UNAUTHORIZED] means the server rejected it. */
fun interface SessionRefresher {
    suspend fun refresh(refreshToken: String): Result<Session, DataError.Network>
}

private val EXPIRY_MARGIN = 30.seconds

/**
 * Hands out a usable access token and renews it. Refresh tokens rotate and reusing one revokes the whole
 * family (ADR 0010), so renewals are serialized: callers that wait on the lock find the new token and do not
 * refresh again.
 */
class SessionManager(
    private val store: SessionStore,
    private val refresher: SessionRefresher,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()

    /** UNAUTHORIZED when there is no session (signed out), without any network call. */
    suspend fun accessToken(): Result<String, DataError.Network> =
        mutex.withLock {
            val session = store.get() ?: return@withLock Result.Error(DataError.Network.UNAUTHORIZED)
            if (session.accessTokenExpiresAt - EXPIRY_MARGIN > clock.now()) {
                Result.Success(session.accessToken)
            } else {
                renew(session)
            }
        }

    /** The server rejected [rejected]. Renews unless another caller already replaced it. */
    suspend fun renewAfterRejection(rejected: String): Result<String, DataError.Network> =
        mutex.withLock {
            val session = store.get() ?: return@withLock Result.Error(DataError.Network.UNAUTHORIZED)
            if (session.accessToken != rejected) Result.Success(session.accessToken) else renew(session)
        }

    /** A rejected refresh token ends the session; any other failure (offline, server down) keeps it for later. */
    private suspend fun renew(session: Session): Result<String, DataError.Network> =
        when (val result = refresher.refresh(session.refreshToken)) {
            is Result.Success -> {
                store.save(result.data)
                Result.Success(result.data.accessToken)
            }

            is Result.Error -> {
                if (result.error == DataError.Network.UNAUTHORIZED) store.clear()
                result
            }
        }
}
