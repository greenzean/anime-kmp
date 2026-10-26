package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val MIN_INTERVAL = 500.milliseconds
private val FIRST_BACKOFF = 30.seconds
private val MAX_BACKOFF = 1.hours
private val FORBIDDEN_BLOCK = 1.hours

class MalThrottle(
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()
    private var nextCallAt = Instant.DISTANT_PAST
    private var backoff = FIRST_BACKOFF
    private var block: MalError? = null
    private var blockedUntil = Instant.DISTANT_PAST

    suspend fun <T> run(call: suspend () -> Result<T, MalApiError>): Result<T, MalError> =
        mutex.withLock {
            val blocked = activeBlock()
            if (blocked != null) return@withLock Result.Error(blocked)
            val wait = nextCallAt - clock.now()
            if (wait > Duration.ZERO) delay(wait)
            val result = call()
            nextCallAt = clock.now() + MIN_INTERVAL
            when (result) {
                is Result.Success -> {
                    backoff = FIRST_BACKOFF
                    result
                }

                is Result.Error -> {
                    Result.Error(toMalError(result.error))
                }
            }
        }

    private fun activeBlock(): MalError? {
        if (clock.now() >= blockedUntil) block = null
        return block
    }

    private fun toMalError(error: MalApiError): MalError =
        when (error) {
            MalApiError.THROTTLED -> {
                val retryAt = clock.now() + backoff
                backoff = minOf(backoff * 2, MAX_BACKOFF)
                MalError.Throttled(retryAt).also { setBlock(it, retryAt) }
            }

            MalApiError.FORBIDDEN -> {
                val until = clock.now() + FORBIDDEN_BLOCK
                MalError.Blocked(until).also { setBlock(it, until) }
            }

            MalApiError.NO_CONNECTION -> {
                MalError.NoConnection
            }

            MalApiError.NOT_FOUND -> {
                MalError.NotFound
            }

            MalApiError.SERVER -> {
                MalError.Server
            }

            MalApiError.SERIALIZATION -> {
                MalError.Serialization
            }

            MalApiError.UNKNOWN -> {
                MalError.Unknown
            }
        }

    private fun setBlock(
        error: MalError,
        until: Instant,
    ) {
        block = error
        blockedUntil = until
    }
}
