package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.AcceptMalConsentUseCase
import com.greenzean.animekmp.shared.mal.domain.ConnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DisconnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.MalAccount
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.MalImportStatus
import com.greenzean.animekmp.shared.mal.domain.ObserveMalAccountUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MalAccountUseCasesTest {
    private var harness = MalAccountHarness()
    private val importScope = CoroutineScope(UnconfinedTestDispatcher())
    private val importHarness = MalImportHarness(importScope)

    @AfterTest
    fun tearDown() {
        importScope.cancel()
        harness.close()
    }

    private fun connect() = ConnectMalUseCase(harness.auth, harness.userApi, harness.accountStore, importHarness.start)

    private fun disconnect() = DisconnectMalUseCase(harness.auth, harness.accountStore, harness.repository)

    @Test
    fun observeCombinesLoginUsernameAndConsent() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")

            val account = ObserveMalAccountUseCase(harness.auth, harness.accountStore)().first()

            assertEquals(MalAccount(connected = true, username = "francisco", consentGiven = true), account)
        }

    @Test
    fun observeWithoutSessionIsNotConnected() =
        runTest {
            val account = ObserveMalAccountUseCase(harness.auth, harness.accountStore)().first()

            assertEquals(MalAccount(connected = false, username = null, consentGiven = false), account)
        }

    @Test
    fun acceptingConsentStoresIt() {
        AcceptMalConsentUseCase(harness.accountStore)()

        assertTrue(harness.accountStore.consentGiven.value)
    }

    @Test
    fun connectLogsInAndStoresTheUsername() =
        runTest {
            val result = connect()()

            assertEquals(Result.Success(Unit), result)
            assertEquals(1, harness.authorizer.calls)
            assertEquals(1, harness.userApi.calls)
            assertEquals("francisco", harness.accountStore.username.value)
            assertTrue(ObserveMalAccountUseCase(harness.auth, harness.accountStore)().first().connected)
        }

    @Test
    fun connectStaysConnectedWithoutUsernameWhenTheUserCallFails() =
        runTest {
            harness = MalAccountHarness(userResult = Result.Error(MalApiError.NO_CONNECTION))

            val result = connect()()

            assertEquals(Result.Success(Unit), result)
            assertNull(harness.accountStore.username.value)
            assertTrue(ObserveMalAccountUseCase(harness.auth, harness.accountStore)().first().connected)
        }

    @Test
    fun connectReturnsTheLoginErrorAndSkipsTheUserCall() =
        runTest {
            harness.authorizer.error = MalAuthError.CANCELLED

            val result = connect()()

            assertEquals(Result.Error(MalAuthError.CANCELLED), result)
            assertEquals(0, harness.userApi.calls)
            assertNull(harness.accountStore.username.value)
            assertFalse(ObserveMalAccountUseCase(harness.auth, harness.accountStore)().first().connected)
        }

    @Test
    fun disconnectLogsOutClearsUsernameAndResetsConsent() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")

            disconnect()()

            assertNull(harness.tokenStore.get())
            assertNull(harness.accountStore.username.value)
            assertFalse(harness.accountStore.consentGiven.value)
            assertFalse(ObserveMalAccountUseCase(harness.auth, harness.accountStore)().first().connected)
        }

    @Test
    fun disconnectClearsTheMalCache() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")
            harness.warmCache()
            assertTrue(harness.repository.details(1) is Result.Success)

            disconnect()()

            assertEquals(Result.Error(MalError.NoConnection), harness.repository.details(1))
        }

    @Test
    fun connectStartsTheImportAfterALogin() =
        runTest {
            importHarness.api.results[0] = listPage(0, 2, hasNext = false)

            connect()()

            assertEquals(listOf(0), importHarness.api.offsets)
            assertEquals(MalImportStatus.Done(2, 0), importHarness.coordinator.status.value)
        }

    @Test
    fun connectStartsTheImportEvenWhenTheUserCallFails() =
        runTest {
            harness = MalAccountHarness(userResult = Result.Error(MalApiError.NO_CONNECTION))

            connect()()

            assertEquals(listOf(0), importHarness.api.offsets)
        }

    @Test
    fun connectDoesNotStartTheImportWhenTheLoginFails() =
        runTest {
            harness.authorizer.error = MalAuthError.CANCELLED

            connect()()

            assertTrue(importHarness.api.calls.isEmpty())
            assertEquals(MalImportStatus.Idle, importHarness.coordinator.status.value)
        }

    @Test
    fun disconnectDoesNotStartAnImport() =
        runTest {
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco")

            disconnect()()

            assertTrue(importHarness.api.calls.isEmpty())
        }
}
