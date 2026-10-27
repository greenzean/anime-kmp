package com.greenzean.animekmp.server.sync

import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncProgressScoreApiTest : SyncApiTestBase() {
    @Test
    fun progressAndScoreRoundTrip() =
        sync {
            val id = UUID.randomUUID()
            val pushed = client.push(change(id, bebop, "WATCHING", "2026-10-19T12:00:00Z", progress = 7, score = 9))
            assertEquals(listOf("APPLIED"), pushed.outcomes())

            val pulled = client.pull(null).changes().single()
            assertEquals("7", pulled.str("progress"))
            assertEquals("9", pulled.str("score"))
        }

    @Test
    fun entryWithoutScorePullsBackNull() =
        sync {
            client.push(change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z", progress = 3))

            val pulled = client.pull(null).changes().single()
            assertEquals("3", pulled.str("progress"))
            assertTrue(pulled.jsonObject["score"] is kotlinx.serialization.json.JsonNull)
        }

    @Test
    fun newerChangeReplacesProgressAndScoreAndClearsTheScore() =
        sync {
            val id = UUID.randomUUID()
            client.push(change(id, bebop, "WATCHING", "2026-10-19T12:00:00Z", progress = 3, score = 6))
            val cursor = client.pull(null).str("nextCursor")
            client.push(change(id, bebop, "COMPLETED", "2026-10-19T13:00:00Z", progress = 26, score = null))

            val pulled = client.pull(cursor).changes().single()
            assertEquals("26", pulled.str("progress"))
            assertTrue(pulled.jsonObject["score"] is kotlinx.serialization.json.JsonNull)
        }

    @Test
    fun staleChangeDoesNotOverwriteProgressAndScore() =
        sync {
            val id = UUID.randomUUID()
            client.push(change(id, bebop, "WATCHING", "2026-10-19T13:00:00Z", progress = 8, score = 7))
            val stale = client.push(change(id, bebop, "WATCHING", "2026-10-19T12:00:00Z", progress = 1, score = 2))
            assertEquals(listOf("IGNORED_STALE"), stale.outcomes())

            val pulled = client.pull(null).changes().single()
            assertEquals("8", pulled.str("progress"))
            assertEquals("7", pulled.str("score"))
        }

    @Test
    fun tombstoneKeepsProgressAndScore() =
        sync {
            val id = UUID.randomUUID()
            client.push(change(id, bebop, "WATCHING", "2026-10-19T12:00:00Z", progress = 4, score = 5))
            client.push(
                change(id, bebop, "WATCHING", "2026-10-19T13:00:00Z", "2026-10-19T13:00:00Z", progress = 4, score = 5),
            )

            val pulled = client.pull(null).changes().single()
            assertEquals("2026-10-19T13:00:00Z", pulled.str("deletedAt"))
            assertEquals("4", pulled.str("progress"))
            assertEquals("5", pulled.str("score"))
        }

    @Test
    fun progressBoundsAreAccepted() =
        sync {
            val result =
                client.push(
                    change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z", progress = 0),
                    change(UUID.randomUUID(), fma, "WATCHING", "2026-10-19T12:00:00Z", progress = 10_000),
                )
            assertEquals(listOf("APPLIED", "APPLIED"), result.outcomes())
        }

    @Test
    fun scoreBoundsAreAccepted() =
        sync {
            val result =
                client.push(
                    change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z", score = 1),
                    change(UUID.randomUUID(), fma, "WATCHING", "2026-10-19T12:00:00Z", score = 10),
                )
            assertEquals(listOf("APPLIED", "APPLIED"), result.outcomes())
        }

    @Test
    fun invalidProgressAndScoreAreRejectedAndStoreNothing() =
        sync {
            val result =
                client.push(
                    change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z", progress = -1),
                    change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z", progress = 10_001),
                    change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z", score = 0),
                    change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z", score = 11),
                )
            assertEquals(List(4) { "REJECTED_INVALID" }, result.outcomes())
            assertEquals(0, client.pull(null).changes().size)
        }

    @Test
    fun invalidChangeDoesNotBlockValidOnesInTheSameBatch() =
        sync {
            val result =
                client.push(
                    change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z", progress = -1),
                    change(UUID.randomUUID(), fma, "WATCHING", "2026-10-19T12:00:00Z", progress = 2, score = 8),
                )
            assertEquals(listOf("REJECTED_INVALID", "APPLIED"), result.outcomes())
            assertEquals(1, client.pull(null).changes().size)
        }

    @Test
    fun repositoryRejectsInvalidProgressAndScore() {
        val repo = SyncRepository(db, clock)
        val at = Instant.parse("2026-10-19T12:00:00Z")
        val results =
            repo.push(
                userA,
                listOf(
                    EntryChange(UUID.randomUUID(), bebop, "WATCHING", at, null, progress = -1, score = null),
                    entryChange(bebop, "WATCHING", at, 10_001, null),
                    entryChange(bebop, "WATCHING", at, 0, 0),
                    entryChange(bebop, "WATCHING", at, 0, 11),
                    entryChange(fma, "WATCHING", at, 10_000, 10),
                ),
            )
        assertEquals(
            List(4) { PushOutcome.REJECTED_INVALID } + PushOutcome.APPLIED,
            results.map { it.outcome },
        )
    }

    @Test
    fun repositoryStoresProgressAndScore() {
        val repo = SyncRepository(db, clock)
        val at = Instant.parse("2026-10-19T12:00:00Z")
        repo.push(userA, listOf(entryChange(bebop, "WATCHING", at, 5, 8)))

        val stored = repo.changesSince(userA, null, 10).changes.single()
        assertEquals(5, stored.progress)
        assertEquals(8, stored.score)
    }
}
