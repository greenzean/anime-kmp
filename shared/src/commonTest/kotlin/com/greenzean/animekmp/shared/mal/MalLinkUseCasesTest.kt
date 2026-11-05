package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.ConnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DisconnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DismissMalPromptUseCase
import com.greenzean.animekmp.shared.mal.domain.LinkMalIfConnectedUseCase
import com.greenzean.animekmp.shared.mal.domain.LinkedPlatformsRepository
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalLinkState
import com.greenzean.animekmp.shared.mal.domain.ObserveMalLinkStateUseCase
import com.greenzean.animekmp.shared.mal.domain.RefreshLinkedPlatformsUseCase
import com.greenzean.animekmp.shared.mal.domain.RequestMalLinkUseCase
import com.greenzean.animekmp.shared.mal.domain.RetryPendingMalLinkUseCase
import com.greenzean.animekmp.shared.mal.domain.UnlinkMalFromAccountUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MalLinkUseCasesTest {
    private var harness = MalAccountHarness()
    private val repository = FakeLinkedPlatformsRepository()
    private val scheduler = FakeSyncScheduler()
    private val importScope = CoroutineScope(UnconfinedTestDispatcher())
    private val importHarness = MalImportHarness(importScope)

    @AfterTest
    fun tearDown() {
        importScope.cancel()
        harness.close()
    }

    private suspend fun linkState(
        linked: Boolean,
        connected: Boolean = false,
        notHere: Boolean = false,
    ): MalLinkState {
        harness.close()
        harness =
            MalAccountHarness(
                session = if (connected) validMalSession() else null,
                notOnThisDevice = notHere,
            )
        val repo = FakeLinkedPlatformsRepository(linked)
        return ObserveMalLinkStateUseCase(repo, harness.auth, harness.accountStore)().first()
    }

    private suspend fun signIn() =
        LinkMalIfConnectedUseCase(
            harness.auth,
            RequestMalLinkUseCase(harness.accountStore, scheduler),
            harness.accountStore,
        )()

    private fun connect() =
        ConnectMalUseCase(
            harness.auth,
            harness.userApi,
            harness.accountStore,
            importHarness.start,
            RequestMalLinkUseCase(harness.accountStore, scheduler),
        )

    @Test
    fun linkedOnTheAccountButNotConnectedHereIsLinkedElsewhere() =
        runTest {
            assertEquals(MalLinkState(linkedToAccount = true, linkedElsewhere = true), linkState(linked = true))
        }

    @Test
    fun notLinkedOnTheAccountIsNotLinkedElsewhere() =
        runTest {
            assertEquals(MalLinkState(linkedToAccount = false, linkedElsewhere = false), linkState(linked = false))
        }

    @Test
    fun connectedOnThisDeviceIsNotLinkedElsewhere() =
        runTest {
            assertEquals(
                MalLinkState(linkedToAccount = true, linkedElsewhere = false),
                linkState(linked = true, connected = true),
            )
        }

    @Test
    fun notOnThisDeviceHidesLinkedElsewhere() =
        runTest {
            assertEquals(
                MalLinkState(linkedToAccount = true, linkedElsewhere = false),
                linkState(linked = true, notHere = true),
            )
        }

    @Test
    fun dismissingThePromptSetsTheFlag() {
        DismissMalPromptUseCase(harness.accountStore)()

        assertTrue(harness.accountStore.notOnThisDevice.value)
    }

    @Test
    fun linkedElsewhereTurnsFalseAfterDismissing() =
        runTest {
            val repo = FakeLinkedPlatformsRepository(true)
            val observe = ObserveMalLinkStateUseCase(repo, harness.auth, harness.accountStore)
            assertTrue(observe().first().linkedElsewhere)

            DismissMalPromptUseCase(harness.accountStore)()

            assertFalse(observe().first().linkedElsewhere)
        }

    @Test
    fun refreshPullsTheAccountFromTheServer() =
        runTest {
            repository.remoteLinked = true

            val result = RefreshLinkedPlatformsUseCase(repository)()

            assertEquals(Result.Success(Unit), result)
            assertTrue(repository.observeMalLinked().first())
        }

    @Test
    fun unlinkingClearsTheServerFlagOnly() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")
            val repo = FakeLinkedPlatformsRepository(true)

            val result = UnlinkMalFromAccountUseCase(repo, harness.accountStore)()

            assertEquals(Result.Success(Unit), result)
            assertFalse(repo.remoteLinked)
            assertTrue(harness.auth.isLoggedIn.first())
            assertEquals("francisco", harness.accountStore.username.value)
            assertTrue(harness.accountStore.consentGiven.value)
        }

    @Test
    fun unlinkingReportsAFailure() =
        runTest {
            val repo = FakeLinkedPlatformsRepository(true).apply { error = DataError.Network.NO_INTERNET }

            assertIs<Result.Error<DataError>>(UnlinkMalFromAccountUseCase(repo, harness.accountStore)())
            assertTrue(repo.remoteLinked)
        }

    @Test
    fun disconnectingLeavesTheServerFlagAlone() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")
            val repo = FakeLinkedPlatformsRepository(true)

            DisconnectMalUseCase(
                harness.auth,
                harness.accountStore,
                harness.repository,
                importHarness.store,
                FakeMalOutbox(),
                FakeMalPullStore(),
                FakeMalBaseStore(),
            )()

            assertEquals(0, repo.unlinkCalls)
            assertTrue(repo.remoteLinked)
            assertFalse(harness.auth.isLoggedIn.first())
        }

    @Test
    fun connectingRequestsTheLinkAfterSuccessWithoutCallingTheServer() =
        runTest {
            val result = connect()()

            assertEquals(Result.Success(Unit), result)
            assertTrue(harness.accountStore.linkPending.value)
            assertEquals(1, scheduler.requests)
            assertEquals(0, repository.linkCalls)
            assertFalse(repository.remoteLinked)
        }

    @Test
    fun connectingCompletesWithoutTouchingTheLinkRepository() =
        runTest {
            val result = connect()()

            assertEquals(Result.Success(Unit), result)
            assertEquals(0, repository.linkCalls)
            assertEquals(0, repository.refreshCalls)
            assertTrue(harness.accountStore.linkPending.value)
        }

    @Test
    fun requestingTheLinkSetsLinkPendingAndAsksForASyncOnly() {
        RequestMalLinkUseCase(harness.accountStore, scheduler)()

        assertTrue(harness.accountStore.linkPending.value)
        assertEquals(1, scheduler.requests)
        assertEquals(0, repository.linkCalls)
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun signingInWithMalConnectedRequestsTheLink() =
        runTest {
            harness.close()
            harness = MalAccountHarness(session = validMalSession())

            signIn()

            assertTrue(harness.accountStore.linkPending.value)
            assertEquals(1, scheduler.requests)
            assertEquals(0, repository.linkCalls)
        }

    @Test
    fun signingInWithoutMalConnectedRequestsNothing() =
        runTest {
            signIn()

            assertFalse(harness.accountStore.linkPending.value)
            assertEquals(0, scheduler.requests)
        }

    @Test
    fun signingInClearsNotOnThisDeviceEvenWithoutMalConnected() =
        runTest {
            harness.close()
            harness = MalAccountHarness(notOnThisDevice = true)

            signIn()

            assertFalse(harness.accountStore.notOnThisDevice.value)
        }

    @Test
    fun signingInClearsNotOnThisDeviceWithMalConnected() =
        runTest {
            harness.close()
            harness = MalAccountHarness(session = validMalSession(), notOnThisDevice = true)

            signIn()

            assertFalse(harness.accountStore.notOnThisDevice.value)
        }

    @Test
    fun connectingWhileTheServerIsDownLeavesLinkPendingAndCallsNoRepository() =
        runTest {
            noInternet()

            val result = connect()()

            assertEquals(Result.Success(Unit), result)
            assertTrue(harness.auth.isLoggedIn.first())
            assertTrue(harness.accountStore.linkPending.value)
            assertEquals(0, repository.linkCalls)
            assertEquals(0, repository.linkIfCalls)
            assertEquals(0, repository.refreshCalls)
        }

    @Test
    fun signingInLeavesLinkPendingAndCallsNoRepository() =
        runTest {
            harness.close()
            harness = MalAccountHarness(session = validMalSession())
            noInternet()

            signIn()

            assertTrue(harness.accountStore.linkPending.value)
            assertEquals(0, repository.linkCalls)
            assertEquals(0, repository.linkIfCalls)
            assertEquals(0, repository.refreshCalls)
        }

    @Test
    fun aFailedLoginDoesNotLink() =
        runTest {
            harness.authorizer.error = MalAuthError.CANCELLED

            val result = connect()()

            assertEquals(Result.Error(MalAuthError.CANCELLED), result)
            assertEquals(0, repository.linkCalls)
            assertFalse(harness.accountStore.linkPending.value)
            assertEquals(0, scheduler.requests)
        }

    private fun noInternet() {
        repository.error = DataError.Network.NO_INTERNET
    }

    @Test
    fun retryDoesNothingWhenNoLinkIsPending() =
        runTest {
            RetryPendingMalLinkUseCase(repository, harness.accountStore)()

            assertEquals(0, repository.linkCalls)
            assertEquals(0, repository.linkIfCalls)
        }

    @Test
    fun retryLinksAndClearsLinkPendingWhenPending() =
        runTest {
            harness = MalAccountHarness(linkPending = true)

            RetryPendingMalLinkUseCase(repository, harness.accountStore)()

            assertEquals(1, repository.linkCalls)
            assertTrue(repository.remoteLinked)
            assertFalse(harness.accountStore.linkPending.value)
        }

    @Test
    fun retryKeepsLinkPendingWhenTheServerIsStillDown() =
        runTest {
            harness = MalAccountHarness(linkPending = true)
            noInternet()

            RetryPendingMalLinkUseCase(repository, harness.accountStore)()

            assertEquals(1, repository.linkCalls)
            assertTrue(harness.accountStore.linkPending.value)
        }

    @Test
    fun retryDoesNotLinkWhenLinkPendingWasClearedBeforeTheLinkRan() =
        runTest {
            harness = MalAccountHarness(linkPending = true)
            val store = harness.accountStore
            val racing =
                object : LinkedPlatformsRepository by repository {
                    override suspend fun linkMalIf(condition: () -> Boolean): EmptyResult<DataError> {
                        store.setLinkPending(false)
                        return repository.linkMalIf(condition)
                    }
                }

            RetryPendingMalLinkUseCase(racing, store)()

            assertEquals(1, repository.linkIfCalls)
            assertEquals(0, repository.linkCalls)
            assertFalse(repository.remoteLinked)
            assertFalse(store.linkPending.value)
        }

    @Test
    fun aSuccessfulUnlinkClearsLinkPending() =
        runTest {
            harness = MalAccountHarness(linkPending = true)
            val repo = FakeLinkedPlatformsRepository(true)

            UnlinkMalFromAccountUseCase(repo, harness.accountStore)()

            assertFalse(harness.accountStore.linkPending.value)
        }

    @Test
    fun aFailedUnlinkKeepsLinkPending() =
        runTest {
            harness = MalAccountHarness(linkPending = true)
            val repo = FakeLinkedPlatformsRepository(true).apply { error = DataError.Network.NO_INTERNET }

            UnlinkMalFromAccountUseCase(repo, harness.accountStore)()

            assertTrue(harness.accountStore.linkPending.value)
        }

    @Test
    fun disconnectingClearsLinkPending() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco", linkPending = true)

            DisconnectMalUseCase(
                harness.auth,
                harness.accountStore,
                harness.repository,
                importHarness.store,
                FakeMalOutbox(),
                FakeMalPullStore(),
                FakeMalBaseStore(),
            )()

            assertFalse(harness.accountStore.linkPending.value)
        }
}
