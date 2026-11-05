package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.core.data.TestAnimeCatalog
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.BackgroundSyncUseCase
import com.greenzean.animekmp.shared.mal.domain.LinkedPlatformsRepository
import com.greenzean.animekmp.shared.mal.domain.RefreshLinkedPlatformsUseCase
import com.greenzean.animekmp.shared.mal.domain.RequestMalLinkUseCase
import com.greenzean.animekmp.shared.mal.domain.RetryPendingMalLinkUseCase
import com.greenzean.animekmp.shared.mal.domain.UnlinkMalFromAccountUseCase
import com.greenzean.animekmp.shared.sync.FakeSyncApi
import com.greenzean.animekmp.shared.sync.data.SqlDelightSyncLocalDataSource
import com.greenzean.animekmp.shared.sync.domain.ChangesPage
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import com.greenzean.animekmp.shared.sync.domain.SyncResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackgroundSyncUseCaseTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver).also(TestAnimeCatalog::apply)
    private val api = FakeSyncApi()
    private val engine = SyncEngine(api, SqlDelightSyncLocalDataSource(db, UnconfinedTestDispatcher()))
    private val repository = FakeLinkedPlatformsRepository()
    private var harness = MalAccountHarness()

    private fun backgroundSync() =
        BackgroundSyncUseCase(
            engine,
            RetryPendingMalLinkUseCase(repository, harness.accountStore),
            RefreshLinkedPlatformsUseCase(repository),
        )

    @AfterTest
    fun tearDown() {
        harness.close()
        driver.close()
    }

    @Test
    fun refreshesLinkedPlatformsAfterASuccessfulSync() =
        runTest {
            val result = backgroundSync()()

            assertIs<SyncResult.Success>(result)
            assertEquals(listOf("pull"), api.calls)
            assertEquals(1, repository.refreshCalls)
        }

    @Test
    fun returnsTheSyncResultUnchanged() =
        runTest {
            api.pages = mutableListOf(Result.Success(ChangesPage("5", false, emptyList())))

            val result = backgroundSync()()

            assertEquals(SyncResult.Success(pushed = 0, pulled = 0, rejected = emptyList()), result)
        }

    @Test
    fun doesNotRefreshWhenTheSyncFails() =
        runTest {
            api.pages = mutableListOf(Result.Error(DataError.Network.NO_INTERNET))

            val result = backgroundSync()()

            assertEquals(SyncResult.Failure(DataError.Network.NO_INTERNET), result)
            assertEquals(0, repository.refreshCalls)
        }

    @Test
    fun linksAPendingAccountLinkAfterASuccessfulSync() =
        runTest {
            harness.close()
            harness = MalAccountHarness(linkPending = true)

            backgroundSync()()

            assertEquals(1, repository.linkCalls)
            assertTrue(repository.remoteLinked)
            assertFalse(harness.accountStore.linkPending.value)
            assertEquals(1, repository.refreshCalls)
        }

    @Test
    fun retriesTheLinkBeforeRefreshing() =
        runTest {
            harness.close()
            harness = MalAccountHarness(linkPending = true)
            val order = mutableListOf<String>()
            val ordered =
                object : LinkedPlatformsRepository by repository {
                    override suspend fun refresh(): EmptyResult<DataError> {
                        order += "refresh"
                        return repository.refresh()
                    }

                    override suspend fun linkMalIf(condition: () -> Boolean): EmptyResult<DataError> {
                        order += "link"
                        return repository.linkMalIf(condition)
                    }
                }
            val useCase =
                BackgroundSyncUseCase(
                    engine,
                    RetryPendingMalLinkUseCase(ordered, harness.accountStore),
                    RefreshLinkedPlatformsUseCase(ordered),
                )

            useCase()

            assertEquals(listOf("link", "refresh"), order)
        }

    @Test
    fun doesNotLinkWhenNothingIsPending() =
        runTest {
            backgroundSync()()

            assertEquals(0, repository.linkCalls)
        }

    @Test
    fun doesNotRetryTheLinkWhenTheSyncFails() =
        runTest {
            harness.close()
            harness = MalAccountHarness(linkPending = true)
            api.pages = mutableListOf(Result.Error(DataError.Network.NO_INTERNET))

            backgroundSync()()

            assertEquals(0, repository.linkCalls)
            assertTrue(harness.accountStore.linkPending.value)
        }

    @Test
    fun keepsLinkPendingWhileTheServerLinkStillFails() =
        runTest {
            harness.close()
            harness = MalAccountHarness(linkPending = true)
            repository.error = DataError.Network.NO_INTERNET

            val result = backgroundSync()()

            assertIs<SyncResult.Success>(result)
            assertEquals(1, repository.linkCalls)
            assertTrue(harness.accountStore.linkPending.value)
        }

    @Test
    fun aPendingLinkStaysPendingWhileTheRepositoryFailsAndLinksOnceItWorks() =
        runTest {
            RequestMalLinkUseCase(harness.accountStore, FakeSyncScheduler())()
            repository.error = DataError.Network.NO_INTERNET

            backgroundSync()()

            assertTrue(harness.accountStore.linkPending.value)
            assertFalse(repository.remoteLinked)
            repository.error = null

            backgroundSync()()

            assertTrue(repository.remoteLinked)
            assertFalse(harness.accountStore.linkPending.value)
        }

    @Test
    fun aRequestedLinkIsPerformedAndClearedByTheNextSuccessfulSync() =
        runTest {
            val scheduler = FakeSyncScheduler()
            RequestMalLinkUseCase(harness.accountStore, scheduler)()
            assertTrue(harness.accountStore.linkPending.value)
            assertEquals(1, scheduler.requests)
            assertEquals(0, repository.linkCalls)

            val result = backgroundSync()()

            assertIs<SyncResult.Success>(result)
            assertEquals(1, repository.linkCalls)
            assertTrue(repository.remoteLinked)
            assertFalse(harness.accountStore.linkPending.value)
        }

    @Test
    fun aRequestedLinkStaysPendingWhenTheSyncFails() =
        runTest {
            RequestMalLinkUseCase(harness.accountStore, FakeSyncScheduler())()
            api.pages = mutableListOf(Result.Error(DataError.Network.NO_INTERNET))

            backgroundSync()()

            assertEquals(0, repository.linkCalls)
            assertTrue(harness.accountStore.linkPending.value)
        }

    @Test
    fun anUnlinkOnAConnectedDeviceIsNotUndoneByABackgroundSync() =
        runTest {
            harness.close()
            harness = MalAccountHarness(validMalSession(), consent = true, username = "francisco", linkPending = true)
            repository.remoteLinked = true
            UnlinkMalFromAccountUseCase(repository, harness.accountStore)()
            assertFalse(repository.remoteLinked)

            backgroundSync()()

            assertEquals(0, repository.linkCalls)
            assertFalse(repository.remoteLinked)
            assertFalse(harness.accountStore.linkPending.value)
        }
}
