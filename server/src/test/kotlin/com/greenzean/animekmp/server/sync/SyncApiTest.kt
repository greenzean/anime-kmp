package com.greenzean.animekmp.server.sync

import com.greenzean.animekmp.server.auth.SessionTokens
import com.greenzean.animekmp.server.db.AnimeTable
import com.greenzean.animekmp.server.db.ListEntryTable
import com.greenzean.animekmp.server.module
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.toKotlinUuid

class SyncApiTest : SyncApiTestBase() {
    @Test
    fun pushedChangeIsPulled() =
        sync {
            val id = UUID.randomUUID()
            val pushed = client.push(change(id, bebop, "WATCHING", "2026-10-19T12:00:00Z"))
            assertEquals(listOf("APPLIED"), pushed.outcomes())

            val page = client.pull(null)
            assertEquals(1, page.changes().size)
            assertEquals(id.toString(), page.changes()[0].str("id"))
            assertEquals("WATCHING", page.changes()[0].str("status"))
            assertEquals("2026-10-19T12:00:00Z", page.changes()[0].str("updatedAt"))
        }

    @Test
    fun replayingABatchChangesNothing() =
        sync {
            val c = change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z")
            client.push(c)
            val cursor = client.pull(null).str("nextCursor")
            val second = client.push(c)
            assertEquals(listOf("IGNORED_STALE"), second.outcomes())
            assertEquals(0, client.pull(cursor).changes().size)
        }

    @Test
    fun newerWinsAndStaleIsIgnored() =
        sync {
            val id = UUID.randomUUID()
            client.push(change(id, bebop, "WATCHING", "2026-10-19T12:00:00Z"))
            val newer = client.push(change(id, bebop, "COMPLETED", "2026-10-19T13:00:00Z"))
            assertEquals(listOf("APPLIED"), newer.outcomes())
            val stale = client.push(change(id, bebop, "DROPPED", "2026-10-19T11:00:00Z"))
            assertEquals(listOf("IGNORED_STALE"), stale.outcomes())

            val page = client.pull(null)
            assertEquals(1, page.changes().size)
            assertEquals("COMPLETED", page.changes()[0].str("status"))
        }

    @Test
    fun tombstoneIsPulledAndCanBeResurrected() =
        sync {
            val id = UUID.randomUUID()
            client.push(change(id, bebop, "WATCHING", "2026-10-19T12:00:00Z"))
            val cursor = client.pull(null).str("nextCursor")
            client.push(change(id, bebop, "WATCHING", "2026-10-19T13:00:00Z", deletedAt = "2026-10-19T13:00:00Z"))

            val afterDelete = client.pull(cursor)
            assertEquals(1, afterDelete.changes().size)
            assertEquals("2026-10-19T13:00:00Z", afterDelete.changes()[0].str("deletedAt"))

            client.push(change(id, bebop, "PLAN_TO_WATCH", "2026-10-19T14:00:00Z"))
            val revived = client.pull(afterDelete.str("nextCursor")).changes()
            assertEquals(1, revived.size)
            assertEquals("PLAN_TO_WATCH", revived[0].str("status"))
            assertTrue(revived[0].jsonObject["deletedAt"] is kotlinx.serialization.json.JsonNull)
        }

    @Test
    fun latePushWithOldTimestampIsStillPulled() =
        sync {
            client.push(change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z"))
            val cursor = client.pull(null).str("nextCursor")
            val late = UUID.randomUUID()
            client.push(change(late, fma, "COMPLETED", "2026-10-19T08:00:00Z"))

            val page = client.pull(cursor)
            assertEquals(listOf(late.toString()), page.changes().map { it.str("id") })
        }

    @Test
    fun pullPages() =
        sync {
            client.push(
                change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z"),
                change(UUID.randomUUID(), fma, "WATCHING", "2026-10-19T12:00:00Z"),
            )
            val first = client.pull(null, limit = 1)
            assertEquals(1, first.changes().size)
            assertEquals("true", first.str("hasMore"))
            val second = client.pull(first.str("nextCursor"), limit = 1)
            assertEquals(1, second.changes().size)
            assertEquals("false", second.str("hasMore"))
            assertNotEquals(first.changes()[0].str("id"), second.changes()[0].str("id"))
        }

    @Test
    fun invalidChangesAreRejected() =
        sync {
            val result =
                client.push(
                    change(UUID.randomUUID(), 0, "WATCHING", "2026-10-19T12:00:00Z"),
                    change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z"),
                    change(UUID.randomUUID(), bebop, "COMPLETED", "2026-10-19T12:30:00Z"),
                )
            assertEquals(listOf("REJECTED_INVALID", "APPLIED", "APPLIED"), result.outcomes())
            assertEquals(1, client.pull(null).changes().size)
        }

    @Test
    fun negativeMalIdIsRejectedAndCreatesNoAnime() =
        sync {
            val result = client.push(change(UUID.randomUUID(), -5, "WATCHING", "2026-10-19T12:00:00Z"))
            assertEquals(listOf("REJECTED_INVALID"), result.outcomes())
            assertEquals(0L, animeRowsFor(-5))
        }

    @Test
    fun unseenMalIdIsAcceptedAndPullsBackAsMalId() =
        sync {
            val unseen = 987654
            val result = client.push(change(UUID.randomUUID(), unseen, "WATCHING", "2026-10-19T12:00:00Z"))
            assertEquals(listOf("APPLIED"), result.outcomes())
            assertEquals(1L, animeRowsFor(unseen))
            assertEquals(
                unseen.toString(),
                client
                    .pull(null)
                    .changes()
                    .single()
                    .str("malId"),
            )
        }

    @Test
    fun twoUsersAddingTheSameAnimeShareOneMapRow() {
        val repo = SyncRepository(db, clock)
        val shared = 424242
        val at = Instant.parse("2026-10-19T12:00:00Z")
        repo.push(userA, listOf(entryChange(shared, "WATCHING", at, 0, null)))
        repo.push(userB, listOf(entryChange(shared, "COMPLETED", at, 0, null)))
        assertEquals(1L, animeRowsFor(shared))
        assertEquals(
            shared,
            repo
                .changesSince(userA, null, 10)
                .changes
                .single()
                .malId,
        )
        assertEquals(
            shared,
            repo
                .changesSince(userB, null, 10)
                .changes
                .single()
                .malId,
        )
    }

    private fun animeRowsFor(malId: Int): Long =
        transaction(db) { AnimeTable.selectAll().where { AnimeTable.malId eq malId }.count() }

    @Test
    fun emptyCursorStartsFromBeginningAndBadCursorErrors() =
        sync {
            client.push(change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z"))
            assertEquals(1, client.pull("").changes().size)
            val bad = client.gql("""{ changesSince(cursor:"zzz") { nextCursor } }""")
            assertTrue(bad.containsKey("errors"))
        }

    @Test
    fun concurrentPushesOfTheSameChangeAreIdempotent() {
        val repo = SyncRepository(db, clock)
        val change = entryChange(bebop, "WATCHING", Instant.parse("2026-10-19T12:00:00Z"), 0, null)
        val outcomes =
            (1..8)
                .map { thread { repo.push(userA, listOf(change)) } }
                .map { it.get() }
                .map { it.single().outcome }
        assertEquals(1, outcomes.count { it == PushOutcome.APPLIED })
        assertEquals(7, outcomes.count { it == PushOutcome.IGNORED_STALE })
        assertEquals(1, repo.changesSince(userA, null, 10).changes.size)
    }

    @Test
    fun concurrentPushesForOneAnimeKeepOneEntry() {
        val repo = SyncRepository(db, clock)
        val results =
            (1..8)
                .map {
                    val change =
                        entryChange(bebop, "WATCHING", Instant.parse("2026-10-19T12:00:00Z"), 0, null)
                    thread { repo.push(userA, listOf(change)) }
                }.map { it.get().single() }
        assertEquals(1, results.count { it.outcome == PushOutcome.APPLIED })
        assertEquals(7, results.count { it.outcome == PushOutcome.IGNORED_STALE })
        assertEquals(1, results.map { it.entryId }.toSet().size)
        assertEquals(1, repo.changesSince(userA, null, 10).changes.size)
    }

    @Test
    fun tombstonedAnimeReaddedUnderANewIdIsMergedIntoTheOldRow() =
        sync {
            val oldId = UUID.randomUUID()
            val newId = UUID.randomUUID()
            client.push(change(oldId, bebop, "WATCHING", "2026-10-19T12:00:00Z", deletedAt = "2026-10-19T12:00:00Z"))
            val cursor = client.pull(null).str("nextCursor")

            val result = client.push(change(newId, bebop, "COMPLETED", "2026-10-19T13:00:00Z"))
            assertEquals(listOf("APPLIED"), result.outcomes())
            assertEquals(oldId.toString(), result["results"]!!.jsonArray[0].str("entryId"))

            val changes = client.pull(cursor).changes()
            assertEquals(listOf(oldId.toString()), changes.map { it.str("id") })
            assertEquals("COMPLETED", changes[0].str("status"))
            assertTrue(changes[0].jsonObject["deletedAt"] is kotlinx.serialization.json.JsonNull)
        }

    @Test
    fun sameAnimeUnderAnotherIdIsMergedByLastWriteWins() =
        sync {
            val existing = UUID.randomUUID()
            client.push(change(existing, bebop, "WATCHING", "2026-10-19T12:00:00Z"))

            val older = client.push(change(UUID.randomUUID(), bebop, "DROPPED", "2026-10-19T11:00:00Z"))
            assertEquals(listOf("IGNORED_STALE"), older.outcomes())
            assertEquals(existing.toString(), older["results"]!!.jsonArray[0].str("entryId"))

            val newer = client.push(change(UUID.randomUUID(), bebop, "COMPLETED", "2026-10-19T13:00:00Z"))
            assertEquals(listOf("APPLIED"), newer.outcomes())
            assertEquals(existing.toString(), newer["results"]!!.jsonArray[0].str("entryId"))

            val rows = client.pull(null).changes()
            assertEquals(listOf(existing.toString()), rows.map { it.str("id") })
            assertEquals("COMPLETED", rows[0].str("status"))
        }

    @Test
    fun retryingAMergedChangeIsIdempotent() =
        sync {
            val oldId = UUID.randomUUID()
            val newId = UUID.randomUUID()
            client.push(change(oldId, bebop, "WATCHING", "2026-10-19T12:00:00Z", deletedAt = "2026-10-19T12:00:00Z"))
            val merge = change(newId, bebop, "COMPLETED", "2026-10-19T13:00:00Z")
            assertEquals(listOf("APPLIED"), client.push(merge).outcomes())
            val cursor = client.pull(null).str("nextCursor")

            val retry = client.push(merge)
            assertEquals(listOf("IGNORED_STALE"), retry.outcomes())
            assertEquals(oldId.toString(), retry["results"]!!.jsonArray[0].str("entryId"))
            assertEquals(0, client.pull(cursor).changes().size)
        }

    @Test
    fun farFutureTimestampsAreRejected() =
        sync {
            val result =
                client.push(
                    change(UUID.randomUUID(), bebop, "WATCHING", "2999-01-01T00:00:00Z"),
                    change(
                        UUID.randomUUID(),
                        fma,
                        "WATCHING",
                        "2026-10-19T12:00:00Z",
                        deletedAt = "2999-01-01T00:00:00Z",
                    ),
                    change(UUID.randomUUID(), 3, "WATCHING", "2026-10-20T00:04:00Z"),
                )
            assertEquals(listOf("REJECTED_INVALID", "REJECTED_INVALID", "APPLIED"), result.outcomes())
        }

    @Test
    fun replayWithSubMillisecondTimestampStaysIdempotent() =
        sync {
            val c = change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00.123456789Z")
            assertEquals(listOf("APPLIED"), client.push(c).outcomes())
            assertEquals(listOf("IGNORED_STALE"), client.push(c).outcomes())
        }

    @Test
    fun existingIdWithAnotherAnimeOrOwnerIsRejected() =
        sync {
            val id = UUID.randomUUID()
            client.push(change(id, bebop, "WATCHING", "2026-10-19T12:00:00Z"))
            val otherAnime = client.push(change(id, fma, "COMPLETED", "2026-10-19T13:00:00Z"))
            assertEquals(listOf("REJECTED_INVALID"), otherAnime.outcomes())

            val foreign = UUID.randomUUID()
            transaction(db) {
                ListEntryTable.insert {
                    it[ListEntryTable.id] = foreign.toKotlinUuid()
                    it[userId] = userB.toKotlinUuid()
                    it[animeId] = AnimeTable.selectAll().where { AnimeTable.malId eq fma }.single()[AnimeTable.id]
                    it[status] = "WATCHING"
                    it[updatedAt] = Instant.parse("2026-10-19T10:00:00Z").atOffset(ZoneOffset.UTC)
                }
            }
            val foreignPush = client.push(change(foreign, fma, "COMPLETED", "2026-10-19T13:00:00Z"))
            assertEquals(listOf("REJECTED_INVALID"), foreignPush.outcomes())
        }

    @Test
    fun oversizedBatchIsAnError() =
        sync {
            val changes = (1..1001).map { change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z") }
            val r =
                client.gql(
                    """mutation { pushChanges(changes: [${changes.joinToString(",")}]) { results { id } } }""",
                )
            assertTrue(r.containsKey("errors"))
        }

    @Test
    fun limitIsClampedToAtLeastOne() =
        sync {
            client.push(
                change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z"),
                change(UUID.randomUUID(), fma, "WATCHING", "2026-10-19T12:00:00Z"),
            )
            val page = client.pull(null, limit = 0)
            assertEquals(1, page.changes().size)
            assertEquals("true", page.str("hasMore"))
        }

    @Test
    fun malIdRejectsNonIntegersAndOverflow() =
        sync {
            for (literal in listOf("99999999999999999999", "1.5", "2147483648")) {
                val id = UUID.randomUUID()
                val input = """{id:"$id",malId:$literal,status:WATCHING,updatedAt:"2026-10-19T12:00:00Z"}"""
                val r = client.gql("mutation { pushChanges(changes: [$input]) { results { id } } }")
                assertTrue(r.containsKey("errors"), "malId $literal should be rejected")
            }
        }

    @Test
    fun usersNeverSeeEachOthersEntries() =
        sync {
            val idA = UUID.randomUUID()
            client.pushAs(userA, change(idA, bebop, "WATCHING", "2026-10-19T12:00:00Z"))
            assertEquals(0, client.pull(null, user = userB).changes().size)

            val idB = UUID.randomUUID()
            client.pushAs(userB, change(idB, fma, "COMPLETED", "2026-10-19T12:00:00Z"))
            assertEquals(listOf(idA.toString()), client.pull(null, user = userA).changes().map { it.str("id") })
            assertEquals(listOf(idB.toString()), client.pull(null, user = userB).changes().map { it.str("id") })
        }

    @Test
    fun theSameAnimeIsSeparatePerUser() =
        sync {
            val idA = UUID.randomUUID()
            val idB = UUID.randomUUID()
            client.pushAs(userA, change(idA, bebop, "WATCHING", "2026-10-19T12:00:00Z"))
            val pushedB = client.pushAs(userB, change(idB, bebop, "DROPPED", "2026-10-19T13:00:00Z"))
            assertEquals(listOf("APPLIED"), pushedB.outcomes())
            assertEquals(
                "WATCHING",
                client
                    .pull(null, user = userA)
                    .changes()
                    .single()
                    .str("status"),
            )
            assertEquals(
                "DROPPED",
                client
                    .pull(null, user = userB)
                    .changes()
                    .single()
                    .str("status"),
            )
        }

    @Test
    fun pushingAnotherUsersEntryIdTouchesNothing() =
        sync {
            val idA = UUID.randomUUID()
            client.pushAs(userA, change(idA, bebop, "WATCHING", "2026-10-19T12:00:00Z"))
            val hijack = client.pushAs(userB, change(idA, bebop, "DROPPED", "2026-10-19T13:00:00Z"))
            assertEquals(listOf("REJECTED_INVALID"), hijack.outcomes())
            assertEquals(
                "WATCHING",
                client
                    .pull(null, user = userA)
                    .changes()
                    .single()
                    .str("status"),
            )
            assertEquals(0, client.pull(null, user = userB).changes().size)
        }

    @Test
    fun unauthenticatedCallsAreRejectedOnBothOperations() =
        sync {
            val expired = SessionTokens(SECRET, Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC))
            val otherSecret = SessionTokens("fedcba9876543210fedcba9876543210", clock)
            val refreshToken = tokens.newRefreshToken().token
            val badTokens =
                listOf(
                    null,
                    "garbage",
                    expired.issueAccess(userA).token,
                    otherSecret.issueAccess(userA).token,
                    refreshToken,
                )
            val input = change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z")
            val queries =
                listOf(
                    "{ changesSince { nextCursor } }",
                    "mutation { pushChanges(changes: [$input]) { results { id } } }",
                )
            for (token in badTokens) {
                for (query in queries) {
                    val error =
                        client
                            .gql(query, token)["errors"]!!
                            .jsonArray
                            .single()
                            .jsonObject
                    assertEquals("UNAUTHENTICATED", error["extensions"]!!.jsonObject.str("code"), "token $token")
                }
            }
            assertEquals(0, transaction(db) { ListEntryTable.selectAll().count() }.toInt())
        }

    @Test
    fun aDeletedUsersTokenIsRejectedByPushAndPull() =
        sync {
            val token = bearer(userA)
            client.push(change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z"))
            transaction(db) { exec("DELETE FROM app_user WHERE id = '$userA'") }
            val input = change(UUID.randomUUID(), fma, "WATCHING", "2026-10-19T12:00:00Z")
            val queries =
                listOf(
                    "{ changesSince { nextCursor } }",
                    "mutation { pushChanges(changes: [$input]) { results { id } } }",
                )
            for (query in queries) {
                val error =
                    client
                        .gql(query, token)["errors"]!!
                        .jsonArray
                        .single()
                        .jsonObject
                assertEquals("UNAUTHENTICATED", error["extensions"]!!.jsonObject.str("code"))
                assertEquals("Authentication failed", error.str("message").substringAfterLast(" : "))
            }
            assertEquals(0, transaction(db) { ListEntryTable.selectAll().count() }.toInt())
        }

    @Test
    fun withoutAuthConfigSyncIsRejected() =
        testApplication {
            application { module(db, clock) }
            val r = client.gql("{ changesSince { nextCursor } }", bearer(userA))
            val error = r["errors"]!!.jsonArray.single().jsonObject
            assertEquals("UNAUTHENTICATED", error["extensions"]!!.jsonObject.str("code"))
        }

    @Test
    fun operationsWithoutAUserStayOpenWithoutAToken() =
        sync {
            val r = client.gql("{ hello }", null)
            assertTrue(!r.containsKey("errors"), r.toString())
        }

    @Test
    fun repositoryTruncatesTimestampsSoReplaysAreIdempotent() {
        val repo = SyncRepository(db, clock)
        val change =
            entryChange(bebop, "WATCHING", Instant.parse("2026-10-19T12:00:00.123456789Z"), 0, null)
        assertEquals(PushOutcome.APPLIED, repo.push(userA, listOf(change)).single().outcome)
        assertEquals(PushOutcome.IGNORED_STALE, repo.push(userA, listOf(change)).single().outcome)
        val stored = repo.changesSince(userA, null, 10).changes.single()
        assertEquals(Instant.parse("2026-10-19T12:00:00.123Z"), stored.updatedAt)
    }
}
