package com.greenzean.animekmp.shared.auth.domain

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class Session(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
)

interface SessionStore {
    val session: Flow<Session?>

    suspend fun get(): Session?

    suspend fun save(session: Session)

    suspend fun clear()
}

fun interface SessionRefresher {
    suspend fun refresh(refreshToken: String): Result<Session, DataError.Network>
}

private val EXPIRY_MARGIN = 30.seconds

class SessionManager(
    private val store: SessionStore,
    private val refresher: SessionRefresher,
    private val clock: Clock = Clock.System,
    private val onSessionEnded: suspend () -> Unit = {},
) {
    private val mutex = Mutex()

    suspend fun accessToken(): Result<String, DataError.Network> =
        mutex.withLock {
            val session = store.get() ?: return@withLock Result.Error(DataError.Network.UNAUTHORIZED)
            if (session.accessTokenExpiresAt - EXPIRY_MARGIN > clock.now()) {
                Result.Success(session.accessToken)
            } else {
                renew(session)
            }
        }

    suspend fun renewAfterRejection(rejected: String): Result<String, DataError.Network> =
        mutex.withLock {
            val session = store.get() ?: return@withLock Result.Error(DataError.Network.UNAUTHORIZED)
            if (session.accessToken != rejected) Result.Success(session.accessToken) else renew(session)
        }

    private suspend fun renew(session: Session): Result<String, DataError.Network> =
        when (val result = refresher.refresh(session.refreshToken)) {
            is Result.Success -> {
                store.save(result.data)
                Result.Success(result.data.accessToken)
            }

            is Result.Error -> {
                if (result.error == DataError.Network.UNAUTHORIZED) endSession()
                result
            }
        }

    private suspend fun endSession() {
        store.clear()
        onSessionEnded()
    }
}
