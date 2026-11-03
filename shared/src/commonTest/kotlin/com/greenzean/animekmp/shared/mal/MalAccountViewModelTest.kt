package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.AcceptMalConsentUseCase
import com.greenzean.animekmp.shared.mal.domain.ConnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DisconnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.MalSendError
import com.greenzean.animekmp.shared.mal.domain.ObserveMalAccountUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalOutboxUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalPullUseCase
import com.greenzean.animekmp.shared.mal.domain.RetryMalSendUseCase
import com.greenzean.animekmp.shared.mal.domain.SyncMalNowUseCase
import com.greenzean.animekmp.shared.mal.presentation.MalAccountAction
import com.greenzean.animekmp.shared.mal.presentation.MalAccountError
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus
import com.greenzean.animekmp.shared.mal.presentation.MalAccountViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class MalAccountViewModelTest {
    private var harness = MalAccountHarness()
    private val importScope = CoroutineScope(UnconfinedTestDispatcher())
    private val importHarness = MalImportHarness(importScope)
    private val pullHarness = MalPullHarness(importScope)
    private val outbox = FakeMalOutbox()
    private val sendScheduler = FakeMalSendScheduler()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        importScope.cancel()
        harness.close()
    }

    private fun TestScope.viewModel(): MalAccountViewModel {
        val viewModel =
            MalAccountViewModel(
                ObserveMalAccountUseCase(harness.auth, harness.accountStore),
                AcceptMalConsentUseCase(harness.accountStore),
                ConnectMalUseCase(harness.auth, harness.userApi, harness.accountStore, importHarness.start),
                DisconnectMalUseCase(
                    harness.auth,
                    harness.accountStore,
                    harness.repository,
                    FakeMalImportProgressStore(),
                    outbox,
                    pullHarness.pullStore,
                    pullHarness.baseStore,
                ),
                ObserveMalOutboxUseCase(outbox),
                RetryMalSendUseCase(outbox, sendScheduler),
                ObserveMalPullUseCase(pullHarness.pullStore, pullHarness.coordinator),
                SyncMalNowUseCase(pullHarness.coordinator),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect { } }
        return viewModel
    }

    private fun status(viewModel: MalAccountViewModel): MalAccountStatus = viewModel.state.value.status

    @Test
    fun withoutConsentTheStatusIsNeedsConsent() =
        runTest {
            assertEquals(MalAccountState(MalAccountStatus.NeedsConsent), viewModel().state.value)
        }

    @Test
    fun acceptingConsentMovesToNotConnected() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(MalAccountAction.OnConsentAccepted)

            assertEquals(MalAccountStatus.NotConnected, status(viewModel))
            assertEquals(true, harness.accountStore.consentGiven.value)
        }

    @Test
    fun connectShowsConnectingWhileTheLoginIsSuspended() =
        runTest {
            harness = MalAccountHarness(consent = true)
            val gate = CompletableDeferred<Unit>()
            harness.authorizer.gate = gate
            val viewModel = viewModel()

            viewModel.onAction(MalAccountAction.OnConnectClick)

            assertEquals(MalAccountStatus.Connecting, status(viewModel))
            gate.complete(Unit)
            assertEquals(MalAccountStatus.Connected("francisco"), status(viewModel))
        }

    @Test
    fun connectedShowsTheUsername() =
        runTest {
            harness = MalAccountHarness(consent = true)
            val viewModel = viewModel()

            viewModel.onAction(MalAccountAction.OnConnectClick)

            assertEquals(MalAccountStatus.Connected("francisco"), status(viewModel))
        }

    @Test
    fun connectedWithoutUsernameWhenTheUserCallFails() =
        runTest {
            harness = MalAccountHarness(consent = true, userResult = Result.Error(MalApiError.SERVER))
            val viewModel = viewModel()

            viewModel.onAction(MalAccountAction.OnConnectClick)

            assertEquals(MalAccountStatus.Connected(null), status(viewModel))
        }

    @Test
    fun cancelledLoginShowsCancelled() = assertLoginError(MalAuthError.CANCELLED, MalAccountError.Cancelled)

    @Test
    fun deniedLoginShowsAccessDenied() = assertLoginError(MalAuthError.ACCESS_DENIED, MalAccountError.AccessDenied)

    @Test
    fun missingBrowserShowsNoBrowser() = assertLoginError(MalAuthError.NO_BROWSER, MalAccountError.NoBrowser)

    @Test
    fun offlineLoginShowsNoInternet() = assertLoginError(MalAuthError.NO_INTERNET, MalAccountError.NoInternet)

    @Test
    fun stateMismatchShowsOther() = assertLoginError(MalAuthError.STATE_MISMATCH, MalAccountError.Other)

    @Test
    fun serverErrorShowsOther() = assertLoginError(MalAuthError.SERVER, MalAccountError.Other)

    @Test
    fun unknownErrorShowsOther() = assertLoginError(MalAuthError.UNKNOWN, MalAccountError.Other)

    @Test
    fun notConfiguredShowsOther() = assertLoginError(MalAuthError.NOT_CONFIGURED, MalAccountError.Other)

    @Test
    fun dismissingTheErrorReturnsToNotConnected() =
        runTest {
            harness = MalAccountHarness(consent = true)
            harness.authorizer.error = MalAuthError.CANCELLED
            val viewModel = viewModel()
            viewModel.onAction(MalAccountAction.OnConnectClick)

            viewModel.onAction(MalAccountAction.OnErrorDismissed)

            assertEquals(MalAccountStatus.NotConnected, status(viewModel))
        }

    @Test
    fun connectingAgainAfterAnErrorCanSucceed() =
        runTest {
            harness = MalAccountHarness(consent = true)
            harness.authorizer.error = MalAuthError.NO_INTERNET
            val viewModel = viewModel()
            viewModel.onAction(MalAccountAction.OnConnectClick)
            harness.authorizer.error = null

            viewModel.onAction(MalAccountAction.OnErrorDismissed)
            viewModel.onAction(MalAccountAction.OnConnectClick)

            assertEquals(MalAccountStatus.Connected("francisco"), status(viewModel))
        }

    @Test
    fun disconnectResetsToNeedsConsent() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")
            val viewModel = viewModel()
            assertEquals(MalAccountStatus.Connected("francisco"), status(viewModel))

            viewModel.onAction(MalAccountAction.OnDisconnectClick)

            assertEquals(MalAccountStatus.NeedsConsent, status(viewModel))
            assertNull(harness.accountStore.username.value)
            assertFalse(harness.accountStore.consentGiven.value)
            assertNull(harness.tokenStore.get())
        }

    @Test
    fun aNewViewModelRestoresConnectedFromTheStores() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")

            assertEquals(MalAccountStatus.Connected("francisco"), status(viewModel()))
        }

    @Test
    fun aNewViewModelRestoresConnectedWithoutUsername() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true)

            assertEquals(MalAccountStatus.Connected(null), status(viewModel()))
        }

    @Test
    fun aNewViewModelRestoresNotConnectedAfterConsent() =
        runTest {
            harness = MalAccountHarness(consent = true)

            assertEquals(MalAccountStatus.NotConnected, status(viewModel()))
        }

    @Test
    fun theStateStartsWithNothingPendingAndNoError() =
        runTest {
            val viewModel = viewModel()

            assertEquals(0, viewModel.state.value.pendingCount)
            assertNull(viewModel.state.value.lastError)
        }

    @Test
    fun pendingCountFollowsTheOutbox() =
        runTest {
            val viewModel = viewModel()

            outbox.emit(pendingCount = 3)

            assertEquals(3, viewModel.state.value.pendingCount)
            assertNull(viewModel.state.value.lastError)
        }

    @Test
    fun lastErrorFollowsTheOutbox() =
        runTest {
            val viewModel = viewModel()

            outbox.emit(pendingCount = 2, lastError = MalSendError.THROTTLED)

            assertEquals(2, viewModel.state.value.pendingCount)
            assertEquals(MalSendError.THROTTLED, viewModel.state.value.lastError)
        }

    @Test
    fun theErrorClearsWhenTheOutboxReportsNone() =
        runTest {
            val viewModel = viewModel()
            outbox.emit(pendingCount = 2, lastError = MalSendError.SERVER)

            outbox.emit(pendingCount = 0)

            assertNull(viewModel.state.value.lastError)
            assertEquals(0, viewModel.state.value.pendingCount)
        }

    @Test
    fun outboxStateAndAccountStatusAreKeptTogether() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")
            val viewModel = viewModel()

            outbox.emit(pendingCount = 1)

            assertEquals(
                MalAccountState(MalAccountStatus.Connected("francisco"), pendingCount = 1),
                viewModel.state.value,
            )
        }

    @Test
    fun retryClearsTheErrorsAndRequestsASend() =
        runTest {
            val viewModel = viewModel()
            outbox.enqueue(MalChange.Remove(1))
            outbox.markFailed(outbox.oldest()!!, MalSendError.NO_CONNECTION)
            assertEquals(MalSendError.NO_CONNECTION, viewModel.state.value.lastError)

            viewModel.onAction(MalAccountAction.OnRetryClick)

            assertEquals(1, outbox.errorClears)
            assertEquals(1, sendScheduler.requests)
            assertNull(viewModel.state.value.lastError)
            assertEquals(1, viewModel.state.value.pendingCount)
        }

    @Test
    fun theStateStartsNeverSyncedAndNotSyncing() =
        runTest {
            val viewModel = viewModel()

            assertNull(viewModel.state.value.lastSyncedAt)
            assertFalse(viewModel.state.value.syncing)
        }

    @Test
    fun lastSyncedAtFollowsThePullStore() =
        runTest {
            val viewModel = viewModel()

            pullHarness.pullStore.setLastPulledAt(PULL_NOW - 2.hours)

            assertEquals(PULL_NOW - 2.hours, viewModel.state.value.lastSyncedAt)
        }

    @Test
    fun syncNowStartsAPullAndTheStateIsSyncingWhileItRuns() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")
            val gate = CompletableDeferred<Unit>()
            pullHarness.api.gates[0] = gate
            val viewModel = viewModel()

            viewModel.onAction(MalAccountAction.OnSyncNowClick)

            assertEquals(listOf(0), pullHarness.api.offsets)
            assertTrue(viewModel.state.value.syncing)
            gate.complete(Unit)
            assertFalse(viewModel.state.value.syncing)
        }

    @Test
    fun afterASyncTheStateShowsTheNewLastSyncedAt() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")
            val viewModel = viewModel()

            viewModel.onAction(MalAccountAction.OnSyncNowClick)

            assertEquals(PULL_NOW, viewModel.state.value.lastSyncedAt)
            assertFalse(viewModel.state.value.syncing)
        }

    @Test
    fun syncNowClickedTwiceWhileSyncingStartsOnlyOnePull() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            pullHarness.api.gates[0] = gate
            val viewModel = viewModel()

            viewModel.onAction(MalAccountAction.OnSyncNowClick)
            viewModel.onAction(MalAccountAction.OnSyncNowClick)
            gate.complete(Unit)

            assertEquals(listOf(0), pullHarness.api.offsets)
        }

    @Test
    fun syncingAndOutboxStateAreKeptTogether() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")
            val gate = CompletableDeferred<Unit>()
            pullHarness.api.gates[0] = gate
            val viewModel = viewModel()
            outbox.emit(pendingCount = 2, lastError = MalSendError.SERVER)

            viewModel.onAction(MalAccountAction.OnSyncNowClick)

            assertEquals(
                MalAccountState(
                    MalAccountStatus.Connected("francisco"),
                    pendingCount = 2,
                    lastError = MalSendError.SERVER,
                    syncing = true,
                ),
                viewModel.state.value,
            )
            gate.complete(Unit)
        }

    @Test
    fun disconnectClearsTheLastPullTime() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")
            pullHarness.pullStore.setLastPulledAt(PULL_NOW)
            val viewModel = viewModel()
            assertEquals(PULL_NOW, viewModel.state.value.lastSyncedAt)

            viewModel.onAction(MalAccountAction.OnDisconnectClick)

            assertNull(viewModel.state.value.lastSyncedAt)
        }

    private fun assertLoginError(
        error: MalAuthError,
        expected: MalAccountError,
    ) = runTest {
        harness = MalAccountHarness(consent = true)
        harness.authorizer.error = error
        val viewModel = viewModel()

        viewModel.onAction(MalAccountAction.OnConnectClick)

        assertEquals(MalAccountStatus.Error(expected), status(viewModel))
    }
}
