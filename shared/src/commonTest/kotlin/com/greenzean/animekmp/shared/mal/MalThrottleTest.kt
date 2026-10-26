package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.MalThrottle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MalThrottleTest {
    private fun TestScope.clock() =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        }

    private val ok: suspend () -> Result<Int, MalApiError> = { Result.Success(1) }

    @Test
    fun spacesCallsAboutHalfASecondApart() =
        runTest {
            val throttle = MalThrottle(clock())
            val startedAt = mutableListOf<Long>()

            repeat(3) {
                throttle.run {
                    startedAt += testScheduler.currentTime
                    Result.Success(1)
                }
            }

            assertEquals(listOf(0L, 500L, 1000L), startedAt)
        }

    @Test
    fun firstThrottleBacksOffThirtySecondsWithoutCallingMal() =
        runTest {
            val throttle = MalThrottle(clock())
            var calls = 0

            val first =
                throttle.run {
                    calls++
                    Result.Error(MalApiError.THROTTLED)
                }
            val second =
                throttle.run {
                    calls++
                    Result.Success(1)
                }

            assertEquals(Result.Error(MalError.Throttled(Instant.fromEpochMilliseconds(30_000))), first)
            assertEquals(first, second)
            assertEquals(1, calls)
        }

    @Test
    fun callsResumeAfterTheBackoffAndItDoublesOnTheNextThrottle() =
        runTest {
            val throttle = MalThrottle(clock())
            throttle.run { Result.Error(MalApiError.THROTTLED) }
            testScheduler.advanceTimeBy(30.seconds)
            testScheduler.runCurrent()

            val second = throttle.run { Result.Error(MalApiError.THROTTLED) }

            val expected = Instant.fromEpochMilliseconds(testScheduler.currentTime) + 60.seconds
            assertEquals(Result.Error(MalError.Throttled(expected)), second)
        }

    @Test
    fun aSuccessResetsTheBackoff() =
        runTest {
            val throttle = MalThrottle(clock())
            throttle.run { Result.Error(MalApiError.THROTTLED) }
            testScheduler.advanceTimeBy(30.seconds)
            testScheduler.runCurrent()
            throttle.run(ok)

            val again = throttle.run { Result.Error(MalApiError.THROTTLED) }

            val expected = Instant.fromEpochMilliseconds(testScheduler.currentTime) + 30.seconds
            assertEquals(Result.Error(MalError.Throttled(expected)), again)
        }

    @Test
    fun forbiddenStopsAllCallsForAnHour() =
        runTest {
            val throttle = MalThrottle(clock())
            var calls = 0

            val first =
                throttle.run {
                    calls++
                    Result.Error(MalApiError.FORBIDDEN)
                }
            testScheduler.advanceTimeBy(59.minutes)
            testScheduler.runCurrent()
            val during =
                throttle.run {
                    calls++
                    Result.Success(1)
                }
            testScheduler.advanceTimeBy(2.minutes)
            testScheduler.runCurrent()
            val after =
                throttle.run {
                    calls++
                    Result.Success(1)
                }

            assertEquals(Result.Error(MalError.Blocked(Instant.fromEpochMilliseconds(0) + 1.hours)), first)
            assertEquals(first, during)
            assertEquals(Result.Success(1), after)
            assertEquals(2, calls)
        }

    @Test
    fun otherErrorsPassThroughWithoutBlocking() =
        runTest {
            val throttle = MalThrottle(clock())

            assertEquals(Result.Error(MalError.NoConnection), throttle.run { Result.Error(MalApiError.NO_CONNECTION) })
            assertEquals(Result.Error(MalError.NotFound), throttle.run { Result.Error(MalApiError.NOT_FOUND) })
            assertEquals(Result.Success(1), throttle.run(ok))
        }
}
