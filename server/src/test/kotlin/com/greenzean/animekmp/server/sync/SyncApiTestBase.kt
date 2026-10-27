package com.greenzean.animekmp.server.sync

import com.greenzean.animekmp.server.DatabaseConfig
import com.greenzean.animekmp.server.auth.SessionTokens
import com.greenzean.animekmp.server.db.AppUserTable
import com.greenzean.animekmp.server.db.connectDatabase
import com.greenzean.animekmp.server.module
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import kotlin.uuid.toKotlinUuid

internal const val CHANGE_FIELDS = "id malId status progress score updatedAt deletedAt"
internal const val SECRET = "0123456789abcdef0123456789abcdef"

internal object SharedDb {
    val db: Database by lazy {
        val container = PostgreSQLContainer("postgres:18-alpine").also { it.start() }
        connectDatabase(DatabaseConfig(container.jdbcUrl, container.username, container.password))
    }
}

abstract class SyncApiTestBase {
    protected val db: Database get() = SharedDb.db

    @AfterTest
    fun shutDownPool() {
        pool.shutdownNow()
    }

    @BeforeTest
    fun setUp() {
        transaction(db) {
            exec("TRUNCATE list_entry, app_user CASCADE")
            for ((id, sub) in listOf(userA to "sub-a", userB to "sub-b")) {
                AppUserTable.insert {
                    it[AppUserTable.id] = id.toKotlinUuid()
                    it[googleSub] = sub
                }
            }
        }
    }

    protected val clock = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC)
    protected val tokens = SessionTokens(SECRET, clock)
    protected val userA = UUID.randomUUID()
    protected val userB = UUID.randomUUID()
    protected val bebop = 1
    protected val fma = 5114

    protected fun change(
        id: UUID,
        malId: Int,
        status: String,
        updatedAt: String,
        deletedAt: String? = null,
        progress: Int = 0,
        score: Int? = null,
    ) = """{id:"$id",malId:$malId,status:$status,progress:$progress,score:${score ?: "null"},""" +
        """updatedAt:"$updatedAt",deletedAt:${deletedAt?.let { "\"$it\"" } ?: "null"}}"""

    protected fun entryChange(
        malId: Int,
        status: String,
        updatedAt: Instant,
        progress: Int = 0,
        score: Int? = null,
    ) = EntryChange(UUID.randomUUID(), malId, status, updatedAt, null, progress, score)

    protected fun bearer(user: UUID) = tokens.issueAccess(user).token

    protected suspend fun HttpClient.gql(
        query: String,
        token: String? = bearer(userA),
    ): JsonObject {
        val body = buildJsonObject { put("query", query) }.toString()
        val text =
            post("/graphql") {
                contentType(ContentType.Application.Json)
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                setBody(body)
            }.bodyAsText()
        return Json.parseToJsonElement(text).jsonObject
    }

    protected suspend fun HttpClient.push(vararg changes: String): JsonObject = pushAs(userA, *changes)

    protected suspend fun HttpClient.pushAs(
        user: UUID,
        vararg changes: String,
    ): JsonObject {
        val r =
            gql(
                """mutation { pushChanges(changes: [${changes.joinToString(
                    ",",
                )}]) { results { id outcome entryId } } }""",
                bearer(user),
            )
        return (r["data"] ?: error("push failed: $r")).jsonObject["pushChanges"]!!.jsonObject
    }

    protected suspend fun HttpClient.pull(
        cursor: String?,
        limit: Int? = null,
        user: UUID = userA,
    ): JsonObject {
        val args = listOfNotNull(cursor?.let { "cursor:\"$it\"" }, limit?.let { "limit:$it" }).joinToString(",")
        val field = if (args.isEmpty()) "changesSince" else "changesSince($args)"
        val r = gql("{ $field { nextCursor hasMore changes { $CHANGE_FIELDS } } }", bearer(user))
        return (r["data"] ?: error("pull failed: $r")).jsonObject["changesSince"]!!.jsonObject
    }

    protected fun JsonObject.outcomes() =
        this["results"]!!.jsonArray.map { it.jsonObject["outcome"]!!.jsonPrimitive.content }

    protected fun JsonObject.changes(): JsonArray = this["changes"]!!.jsonArray

    protected fun JsonElement.str(key: String) = jsonObject[key]!!.jsonPrimitive.content

    protected fun sync(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { module(db, clock, tokens = tokens) }
            block()
        }

    protected fun <T> thread(block: () -> T): java.util.concurrent.Future<T> = pool.submit(block)

    protected val pool =
        java.util.concurrent.Executors
            .newFixedThreadPool(8)
}
