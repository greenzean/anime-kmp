package com.greenzean.animekmp.server.sync

import com.greenzean.animekmp.server.DatabaseConfig
import com.greenzean.animekmp.server.db.ListEntryTable
import com.greenzean.animekmp.server.db.connectDatabase
import com.greenzean.animekmp.server.module
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.toKotlinUuid

private const val CHANGE_FIELDS = "id animeId status updatedAt deletedAt"

/** One Postgres container for the whole class; the container is reaped by Testcontainers when the JVM exits. */
private object SharedDb {
    val db: Database by lazy {
        val container = PostgreSQLContainer("postgres:18-alpine").also { it.start() }
        connectDatabase(DatabaseConfig(container.jdbcUrl, container.username, container.password))
    }
}

class SyncApiTest {
    private val db: Database get() = SharedDb.db

    @AfterTest
    fun shutDownPool() {
        pool.shutdownNow()
    }

    @BeforeTest
    fun setUp() {
        transaction(db) { exec("TRUNCATE list_entry") }
    }

    // Fixed "now" so test timestamps are never in the future relative to the server (future timestamps are rejected).
    private val clock = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC)
    private val bebop = 1L
    private val fma = 2L

    private fun change(
        id: UUID,
        animeId: Long,
        status: String,
        updatedAt: String,
        deletedAt: String? = null,
    ) = """{id:"$id",animeId:$animeId,status:$status,updatedAt:"$updatedAt",deletedAt:${deletedAt?.let {
        "\"$it\""
    } ?: "null"}}"""

    private suspend fun HttpClient.gql(query: String): JsonObject {
        val body = buildJsonObject { put("query", query) }.toString()
        val text =
            post("/graphql") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun HttpClient.push(vararg changes: String): JsonObject {
        val r =
            gql(
                """mutation { pushChanges(changes: [${changes.joinToString(
                    ",",
                )}]) { results { id outcome entryId } } }""",
            )
        return (r["data"] ?: error("push failed: $r")).jsonObject["pushChanges"]!!.jsonObject
    }

    private suspend fun HttpClient.pull(
        cursor: String?,
        limit: Int? = null,
    ): JsonObject {
        val args = listOfNotNull(cursor?.let { "cursor:\"$it\"" }, limit?.let { "limit:$it" }).joinToString(",")
        val field = if (args.isEmpty()) "changesSince" else "changesSince($args)"
        val r = gql("{ $field { nextCursor hasMore changes { $CHANGE_FIELDS } } }")
        return (r["data"] ?: error("pull failed: $r")).jsonObject["changesSince"]!!.jsonObject
    }

    private fun JsonObject.outcomes() =
        this["results"]!!.jsonArray.map { it.jsonObject["outcome"]!!.jsonPrimitive.content }

    private fun JsonObject.changes(): JsonArray = this["changes"]!!.jsonArray

    private fun JsonElement.str(key: String) = jsonObject[key]!!.jsonPrimitive.content

    private fun sync(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { module(db, clock) }
            block()
        }

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
            // Another device pushes a change it made hours ago, after this client already pulled.
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
                    change(UUID.randomUUID(), 9999, "WATCHING", "2026-10-19T12:00:00Z"),
                    change(UUID.randomUUID(), bebop, "WATCHING", "2026-10-19T12:00:00Z"),
                    change(UUID.randomUUID(), bebop, "COMPLETED", "2026-10-19T12:30:00Z"),
                )
            // The third change is the same anime as the second, so it is merged into that row (newer, so APPLIED).
            assertEquals(listOf("REJECTED_UNKNOWN_ANIME", "APPLIED", "APPLIED"), result.outcomes())
            assertEquals(1, client.pull(null).changes().size)
        }

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
        val change = EntryChange(UUID.randomUUID(), bebop, "WATCHING", Instant.parse("2026-10-19T12:00:00Z"), null)
        val outcomes =
            (1..8)
                .map { thread { repo.push(DevUser.id, listOf(change)) } }
                .map { it.get() }
                .map { it.single().outcome }
        assertEquals(1, outcomes.count { it == PushOutcome.APPLIED })
        assertEquals(7, outcomes.count { it == PushOutcome.IGNORED_STALE })
        assertEquals(1, repo.changesSince(DevUser.id, null, 10).changes.size)
    }

    @Test
    fun concurrentPushesForOneAnimeKeepOneEntry() {
        val repo = SyncRepository(db, clock)
        val results =
            (1..8)
                .map {
                    val change =
                        EntryChange(UUID.randomUUID(), bebop, "WATCHING", Instant.parse("2026-10-19T12:00:00Z"), null)
                    thread { repo.push(DevUser.id, listOf(change)) }
                }.map { it.get().single() }
        assertEquals(1, results.count { it.outcome == PushOutcome.APPLIED })
        assertEquals(7, results.count { it.outcome == PushOutcome.IGNORED_STALE })
        assertEquals(1, results.map { it.entryId }.toSet().size)
        assertEquals(1, repo.changesSince(DevUser.id, null, 10).changes.size)
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

            // The response was lost, so the client sends the same change again.
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
                    it[userId] = UUID.randomUUID().toKotlinUuid()
                    it[animeId] = fma
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
    fun longScalarRejectsNonIntegersAndOverflow() =
        sync {
            for (literal in listOf("99999999999999999999", "1.5")) {
                val id = UUID.randomUUID()
                val input = """{id:"$id",animeId:$literal,status:WATCHING,updatedAt:"2026-10-19T12:00:00Z"}"""
                val r = client.gql("mutation { pushChanges(changes: [$input]) { results { id } } }")
                assertTrue(r.containsKey("errors"), "animeId $literal should be rejected")
            }
        }

    @Test
    fun repositoryTruncatesTimestampsSoReplaysAreIdempotent() {
        val repo = SyncRepository(db, clock)
        val change =
            EntryChange(UUID.randomUUID(), bebop, "WATCHING", Instant.parse("2026-10-19T12:00:00.123456789Z"), null)
        assertEquals(PushOutcome.APPLIED, repo.push(DevUser.id, listOf(change)).single().outcome)
        assertEquals(PushOutcome.IGNORED_STALE, repo.push(DevUser.id, listOf(change)).single().outcome)
        val stored = repo.changesSince(DevUser.id, null, 10).changes.single()
        assertEquals(Instant.parse("2026-10-19T12:00:00.123Z"), stored.updatedAt)
    }

    private fun <T> thread(block: () -> T): java.util.concurrent.Future<T> = pool.submit(block)

    private val pool =
        java.util.concurrent.Executors
            .newFixedThreadPool(8)
}
