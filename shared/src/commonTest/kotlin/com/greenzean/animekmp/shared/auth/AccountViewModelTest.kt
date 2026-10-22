package com.greenzean.animekmp.shared.auth

import com.greenzean.animekmp.shared.auth.data.InMemorySessionStore
import com.greenzean.animekmp.shared.auth.domain.AuthApi
import com.greenzean.animekmp.shared.auth.domain.GoogleIdTokenProvider
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.auth.domain.SignInError
import com.greenzean.animekmp.shared.auth.domain.SignInWithGoogleUseCase
import com.greenzean.animekmp.shared.auth.presentation.AccountAction
import com.greenzean.animekmp.shared.auth.presentation.AccountViewModel
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.time.Instant

private val SESSION = Session("access", Instant.parse("2026-10-22T12:15:00Z"), "refresh")

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val store = InMemorySessionStore()
    private val api = FakeAuthApi()
    private val provider = FakeGoogleIdTokenProvider()
    private val scheduler = FakeSyncScheduler()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AccountViewModel(store, SignInWithGoogleUseCase(provider, api, store, scheduler))

    @Test
    fun startsSignedOut() {
        val state = viewModel().state.value
        assertFalse(state.isSignedIn)
        assertFalse(state.isSigningIn)
        assertNull(state.error)
    }

    @Test
    fun startsSignedInWhenASessionIsStored() =
        runTest {
            store.save(SESSION)
            assertTrue(viewModel().state.value.isSignedIn)
        }

    @Test
    fun signingInSavesTheSessionAndRequestsSync() {
        val viewModel = viewModel()
        viewModel.onAction(AccountAction.OnSignInWithGoogleClick)

        assertTrue(viewModel.state.value.isSignedIn)
        assertFalse(viewModel.state.value.isSigningIn)
        assertNull(viewModel.state.value.error)
        assertEquals("nonce-1", provider.nonces.single())
        assertEquals("id-token", api.idTokens.single())
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun isSigningInWhileGoogleIsOpen() {
        val gate = CompletableDeferred<Unit>()
        provider.gate = gate
        val viewModel = viewModel()
        viewModel.onAction(AccountAction.OnSignInWithGoogleClick)
        assertTrue(viewModel.state.value.isSigningIn)

        viewModel.onAction(AccountAction.OnSignInWithGoogleClick)
        assertEquals(1, provider.nonces.size)

        gate.complete(Unit)
        assertFalse(viewModel.state.value.isSigningIn)
        assertTrue(viewModel.state.value.isSignedIn)
    }

    @Test
    fun cancellingShowsNoError() {
        provider.result = Result.Error(GoogleSignInError.CANCELLED)
        val viewModel = viewModel()
        viewModel.onAction(AccountAction.OnSignInWithGoogleClick)

        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSignedIn)
        assertFalse(viewModel.state.value.isSigningIn)
    }

    @Test
    fun noGoogleAccountShowsAnError() {
        provider.result = Result.Error(GoogleSignInError.NO_CREDENTIAL)
        val viewModel = viewModel()
        viewModel.onAction(AccountAction.OnSignInWithGoogleClick)

        assertEquals(SignInError.Google(GoogleSignInError.NO_CREDENTIAL), viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSignedIn)
        assertEquals(0, scheduler.requests)
    }

    @Test
    fun aRejectedIdTokenShowsAnErrorAndSavesNothing() {
        api.signInResult = Result.Error(DataError.Network.UNAUTHORIZED)
        val viewModel = viewModel()
        viewModel.onAction(AccountAction.OnSignInWithGoogleClick)

        assertEquals(SignInError.Server(DataError.Network.UNAUTHORIZED), viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSignedIn)
        assertEquals(0, scheduler.requests)
    }

    @Test
    fun noNonceMeansGoogleIsNeverAsked() {
        api.nonceResult = Result.Error(DataError.Network.NO_INTERNET)
        val viewModel = viewModel()
        viewModel.onAction(AccountAction.OnSignInWithGoogleClick)

        assertEquals(SignInError.Server(DataError.Network.NO_INTERNET), viewModel.state.value.error)
        assertEquals(emptyList(), provider.nonces)
    }

    @Test
    fun dismissingClearsTheError() {
        provider.result = Result.Error(GoogleSignInError.UNKNOWN)
        val viewModel = viewModel()
        viewModel.onAction(AccountAction.OnSignInWithGoogleClick)
        viewModel.onAction(AccountAction.OnDismissError)

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun aSessionClearedBehindTheScenesSignsOut() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(AccountAction.OnSignInWithGoogleClick)
            assertTrue(viewModel.state.value.isSignedIn)

            store.clear()

            assertFalse(viewModel.state.value.isSignedIn)
        }
}

private class FakeAuthApi : AuthApi {
    var nonceResult: Result<String, DataError.Network> = Result.Success("nonce-1")
    var signInResult: Result<Session, DataError.Network> = Result.Success(SESSION)
    val idTokens = mutableListOf<String>()

    override suspend fun signInNonce() = nonceResult

    override suspend fun signInWithGoogle(idToken: String): Result<Session, DataError.Network> {
        idTokens += idToken
        return signInResult
    }
}

private class FakeGoogleIdTokenProvider : GoogleIdTokenProvider {
    var result: Result<String, GoogleSignInError> = Result.Success("id-token")
    var gate: CompletableDeferred<Unit>? = null
    val nonces = mutableListOf<String>()

    override suspend fun getIdToken(nonce: String): Result<String, GoogleSignInError> {
        nonces += nonce
        gate?.await()
        return result
    }
}

private class FakeSyncScheduler : SyncScheduler {
    var requests = 0

    override fun requestSync() {
        requests++
    }
}
