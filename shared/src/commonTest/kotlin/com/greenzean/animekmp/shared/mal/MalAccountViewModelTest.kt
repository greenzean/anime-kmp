package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.AcceptMalConsentUseCase
import com.greenzean.animekmp.shared.mal.domain.ConnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DisconnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.ObserveMalAccountUseCase
import com.greenzean.animekmp.shared.mal.presentation.MalAccountAction
import com.greenzean.animekmp.shared.mal.presentation.MalAccountError
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus
import com.greenzean.animekmp.shared.mal.presentation.MalAccountViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class MalAccountViewModelTest {
    private var harness = MalAccountHarness()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        harness.close()
    }

    private fun TestScope.viewModel(): MalAccountViewModel {
        val viewModel =
            MalAccountViewModel(
                ObserveMalAccountUseCase(harness.auth, harness.accountStore),
                AcceptMalConsentUseCase(harness.accountStore),
                ConnectMalUseCase(harness.auth, harness.userApi, harness.accountStore),
                DisconnectMalUseCase(harness.auth, harness.accountStore, harness.repository),
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
