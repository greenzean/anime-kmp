package com.greenzean.animekmp.shared.auth

import com.greenzean.animekmp.shared.auth.data.InMemorySessionStore
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.auth.domain.MergeLibraryUseCase
import com.greenzean.animekmp.shared.auth.domain.MergeSummary
import com.greenzean.animekmp.shared.auth.domain.SignInError
import com.greenzean.animekmp.shared.auth.domain.SignInWithGoogleUseCase
import com.greenzean.animekmp.shared.auth.domain.SignOutUseCase
import com.greenzean.animekmp.shared.auth.presentation.AccountAction
import com.greenzean.animekmp.shared.auth.presentation.AccountViewModel
import com.greenzean.animekmp.shared.auth.presentation.MergeState
import com.greenzean.animekmp.shared.auth.presentation.SignOutPrompt
import com.greenzean.animekmp.shared.core.data.DevAnimeSeed
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.sync.FakeServerSyncApi
import com.greenzean.animekmp.shared.sync.data.SqlDelightAccountLibraryDataSource
import com.greenzean.animekmp.shared.sync.data.SqlDelightSyncLocalDataSource
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
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

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val store = InMemorySessionStore()
    private val api = FakeAuthApi()
    private val provider = FakeGoogleIdTokenProvider()
    private val scheduler = FakeSyncScheduler()
    private val server = FakeServerSyncApi()
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver).also(DevAnimeSeed::apply)
    private val local = SqlDelightSyncLocalDataSource(db, UnconfinedTestDispatcher())
    private val accountLibrary = SqlDelightAccountLibraryDataSource(db, UnconfinedTestDispatcher())
    private val engine = SyncEngine(server, local)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        driver.close()
    }

    private fun viewModel() =
        AccountViewModel(
            sessionStore = store,
            signInWithGoogle = SignInWithGoogleUseCase(provider, api, store),
            mergeLibrary = MergeLibraryUseCase(engine, accountLibrary, scheduler),
            signOut = SignOutUseCase(store, engine, accountLibrary),
        )

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
    fun signingInSavesTheSessionAndMergesTheLibrary() {
        db.listEntryQueries.insertPending("e1", 1, "WATCHING", 10)
        val viewModel = viewModel()
        viewModel.onAction(AccountAction.OnSignInWithGoogleClick)

        assertTrue(viewModel.state.value.isSignedIn)
        assertFalse(viewModel.state.value.isSigningIn)
        assertNull(viewModel.state.value.error)
        assertEquals("nonce-1", provider.nonces.single())
        assertEquals("id-token", api.idTokens.single())
        assertEquals(MergeState.Done(MergeSummary(sent = 1, rejected = 0, total = 1)), viewModel.state.value.merge)
        assertEquals(0, scheduler.requests)
    }

    @Test
    fun aMergeWithoutConnectionStaysSignedInAndCanBeRetried() {
        server.offline = true
        db.listEntryQueries.insertPending("e1", 1, "WATCHING", 10)
        val viewModel = viewModel()
        viewModel.onAction(AccountAction.OnSignInWithGoogleClick)

        assertTrue(viewModel.state.value.isSignedIn)
        assertEquals(MergeState.Failed(DataError.Network.NO_INTERNET), viewModel.state.value.merge)
        assertEquals(1, scheduler.requests)

        server.offline = false
        viewModel.onAction(AccountAction.OnRetryMerge)
        assertEquals(MergeState.Done(MergeSummary(sent = 1, rejected = 0, total = 1)), viewModel.state.value.merge)

        viewModel.onAction(AccountAction.OnDismissMerge)
        assertEquals(MergeState.Idle, viewModel.state.value.merge)
    }

    @Test
    fun signOutAsksFirstThenKeepsTheLibrary() {
        db.listEntryQueries.insertPending("e1", 1, "WATCHING", 10)
        val viewModel = signedInViewModel()

        viewModel.onAction(AccountAction.OnSignOutClick)
        assertEquals(SignOutPrompt.Ready(unsyncedCount = 0), viewModel.state.value.signOutPrompt)
        assertTrue(viewModel.state.value.isSignedIn)

        viewModel.onAction(AccountAction.OnKeepLibraryClick)
        assertNull(viewModel.state.value.signOutPrompt)
        assertFalse(viewModel.state.value.isSignedIn)
        assertEquals(1L, db.listEntryQueries.countActive().executeAsOne())
    }

    @Test
    fun signOutWithDeleteEmptiesTheLibrary() {
        db.listEntryQueries.insertPending("e1", 1, "WATCHING", 10)
        val viewModel = signedInViewModel()

        viewModel.onAction(AccountAction.OnSignOutClick)
        viewModel.onAction(AccountAction.OnDeleteLibraryClick)

        assertFalse(viewModel.state.value.isSignedIn)
        assertEquals(MergeState.Idle, viewModel.state.value.merge)
        assertEquals(0L, db.listEntryQueries.countActive().executeAsOne())
    }

    @Test
    fun signOutOfflineWarnsAboutUnsyncedChanges() {
        val viewModel = signedInViewModel()
        db.listEntryQueries.insertPending("e1", 1, "WATCHING", 10)
        server.offline = true

        viewModel.onAction(AccountAction.OnSignOutClick)

        assertEquals(SignOutPrompt.Ready(unsyncedCount = 1), viewModel.state.value.signOutPrompt)
    }

    @Test
    fun cancellingSignOutChangesNothing() {
        db.listEntryQueries.insertPending("e1", 1, "WATCHING", 10)
        val viewModel = signedInViewModel()

        viewModel.onAction(AccountAction.OnSignOutClick)
        viewModel.onAction(AccountAction.OnDismissSignOut)

        assertNull(viewModel.state.value.signOutPrompt)
        assertTrue(viewModel.state.value.isSignedIn)
        assertEquals(1L, db.listEntryQueries.countActive().executeAsOne())
    }

    @Test
    fun dismissingWhileTheLibraryIsStillSyncingClosesTheDialogForGood() {
        val viewModel = signedInViewModel()
        val gate = CompletableDeferred<Unit>()
        server.pullGate = gate

        viewModel.onAction(AccountAction.OnSignOutClick)
        assertEquals(SignOutPrompt.Preparing, viewModel.state.value.signOutPrompt)
        viewModel.onAction(AccountAction.OnDismissSignOut)
        gate.complete(Unit)

        assertNull(viewModel.state.value.signOutPrompt)
        assertTrue(viewModel.state.value.isSignedIn)
    }

    @Test
    fun aSessionThatEndsOnItsOwnClearsTheMergeCard() {
        val viewModel = signedInViewModel()
        assertTrue(viewModel.state.value.merge is MergeState.Done)

        runTest { store.clear() }

        assertFalse(viewModel.state.value.isSignedIn)
        assertEquals(MergeState.Idle, viewModel.state.value.merge)
    }

    private fun signedInViewModel() = viewModel().also { it.onAction(AccountAction.OnSignInWithGoogleClick) }

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
