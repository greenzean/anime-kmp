package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.mal.domain.PullMalIfStaleUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PullMalIfStaleUseCaseTest {
    private fun TestScope.harness(
        loggedIn: Boolean = true,
        lastPulledAt: Instant? = null,
    ) = MalPullHarness(
        backgroundScope + UnconfinedTestDispatcher(testScheduler),
        loggedIn,
        lastPulledAt = lastPulledAt,
    ).also { it.api.gates[0] = CompletableDeferred() }

    private fun MalPullHarness.ifStale() = PullMalIfStaleUseCase(auth, pullStore, coordinator, clock)

    @Test
    fun neverPulledStartsAPull() =
        runTest {
            val h = harness(lastPulledAt = null)

            h.ifStale()()

            assertEquals(listOf(0), h.api.offsets)
        }

    @Test
    fun aPullOlderThanFifteenMinutesStartsAPull() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW - 16.minutes)

            h.ifStale()()

            assertEquals(listOf(0), h.api.offsets)
        }

    @Test
    fun aPullFromYesterdayStartsAPull() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW - 1.days)

            h.ifStale()()

            assertEquals(listOf(0), h.api.offsets)
        }

    @Test
    fun aPullOneSecondOverFifteenMinutesIsStale() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW - 15.minutes - 1.seconds)

            h.ifStale()()

            assertEquals(listOf(0), h.api.offsets)
        }

    @Test
    fun exactlyFifteenMinutesIsNotStaleBecauseStaleMeansOlderThanFifteenMinutes() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW - 15.minutes)

            h.ifStale()()

            assertEquals(emptyList(), h.api.offsets)
        }

    @Test
    fun aPullOneSecondUnderFifteenMinutesIsFresh() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW - 15.minutes + 1.seconds)

            h.ifStale()()

            assertEquals(emptyList(), h.api.offsets)
        }

    @Test
    fun aRecentPullDoesNotStartAPull() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW - 2.minutes)

            h.ifStale()()

            assertEquals(emptyList(), h.api.offsets)
        }

    @Test
    fun notConnectedNeverStartsAPullEvenWhenNeverPulled() =
        runTest {
            val h = harness(loggedIn = false, lastPulledAt = null)

            h.ifStale()()

            assertEquals(emptyList(), h.api.offsets)
        }

    @Test
    fun notConnectedNeverStartsAPullWhenTheLastPullIsOld() =
        runTest {
            val h = harness(loggedIn = false, lastPulledAt = PULL_NOW - 3.hours)

            h.ifStale()()

            assertEquals(emptyList(), h.api.offsets)
        }
}
