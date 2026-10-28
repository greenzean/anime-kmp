package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.AcceptMalConsentUseCase
import com.greenzean.animekmp.shared.mal.domain.ConnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DisconnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.MalAccount
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.ObserveMalAccountUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MalAccountUseCasesTest {
    private var harness = MalAccountHarness()

    @AfterTest
    fun tearDown() = harness.close()

    private fun connect() = ConnectMalUseCase(harness.auth, harness.userApi, harness.accountStore)

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
}
