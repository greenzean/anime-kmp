package com.greenzean.animekmp.server.auth

import com.greenzean.animekmp.server.DatabaseConfig
import com.greenzean.animekmp.server.db.AppUserTable
import com.greenzean.animekmp.server.db.RefreshTokenTable
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.toJavaUuid

private const val SECRET = "0123456789abcdef0123456789abcdef"
private const val PAYLOAD_FIELDS = "accessToken accessTokenExpiresAt refreshToken refreshTokenExpiresAt"

private object AuthDb {
    val db: Database by lazy {
        val container = PostgreSQLContainer("postgres:18-alpine").also { it.start() }
        connectDatabase(DatabaseConfig(container.jdbcUrl, container.username, container.password))
    }
}

private class MutableClock(
    var now: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

class AuthApiTest {
    private val db: Database get() = AuthDb.db
    private val key = newRsaKey()
    private val clock = MutableClock(TEST_NOW)
    private val tokens = SessionTokens(SECRET, clock)
    private val service =
        AuthService(
            GoogleIdTokenVerifier(TEST_CLIENT_ID, FakeGoogleKeys(key.first), clock),
            tokens,
            AuthRepository(db, clock),
        )

    @BeforeTest
    fun setUp() {
        transaction(db) { exec("TRUNCATE app_user, sign_in_nonce, refresh_token CASCADE") }
    }

    private fun authApp(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { module(db, clock, service, tokens) }
            block()
        }

    private suspend fun HttpClient.gql(
        query: String,
        accessToken: String? = null,
    ): JsonObject {
        val body = buildJsonObject { put("query", query) }.toString()
        val text =
            post("/graphql") {
                contentType(ContentType.Application.Json)
                accessToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                setBody(body)
            }.bodyAsText()
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun HttpClient.nonce(): String =
        gql("mutation { signInNonce }")["data"]!!.jsonObject["signInNonce"]!!.jsonPrimitive.content

    private suspend fun HttpClient.signIn(idToken: String): JsonObject =
        gql("""mutation { signInWithGoogle(idToken: "$idToken") { $PAYLOAD_FIELDS } }""")

    private suspend fun HttpClient.refresh(refreshToken: String): JsonObject =
        gql("""mutation { refreshSession(refreshToken: "$refreshToken") { $PAYLOAD_FIELDS } }""")

    private suspend fun HttpClient.signOut(refreshToken: String): JsonObject =
        gql("""mutation { signOut(refreshToken: "$refreshToken") }""")

    private suspend fun HttpClient.deleteNonce(): String =
        gql("mutation { deleteAccountNonce }")["data"]!!.jsonObject["deleteAccountNonce"]!!.jsonPrimitive.content

    private suspend fun HttpClient.deleteAccount(idToken: String): JsonObject =
        gql("""mutation { deleteAccount(idToken: "$idToken") }""")

    private fun userId(sub: String): UUID =
        transaction(db) {
            AppUserTable
                .selectAll()
                .where { AppUserTable.googleSub eq sub }
                .single()[AppUserTable.id]
                .toJavaUuid()
        }

    private fun seedEntries(
        userId: UUID,
        firstMalId: Int,
    ) {
        transaction(db) {
            exec("INSERT INTO anime (mal_id) VALUES ($firstMalId), (${firstMalId + 1}) ON CONFLICT DO NOTHING")
            exec(
                "INSERT INTO list_entry (id, user_id, anime_id, status, updated_at) VALUES " +
                    "(gen_random_uuid(), '$userId', (SELECT id FROM anime WHERE mal_id = $firstMalId), " +
                    "'WATCHING', now())",
            )
            exec(
                "INSERT INTO list_entry (id, user_id, anime_id, status, updated_at, deleted_at) VALUES " +
                    "(gen_random_uuid(), '$userId', (SELECT id FROM anime WHERE mal_id = ${firstMalId + 1}), " +
                    "'DROPPED', now(), now())",
            )
        }
    }

    private fun rowsFor(
        table: String,
        userId: UUID,
    ): Long =
        transaction(db) {
            val column = if (table == "app_user") "id" else "user_id"
            var count = 0L
            exec("SELECT count(*) FROM $table WHERE $column = '$userId'") { rs ->
                rs.next()
                count = rs.getLong(1)
            }
            count
        }

    private fun JsonObject.payload(field: String) = this["data"]!!.jsonObject[field]!!.jsonObject

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content

    private fun JsonObject.assertUnauthenticated() {
        val error = this["errors"]!!.jsonArray.single().jsonObject
        assertTrue(error.str("message").endsWith("Authentication failed"))
        assertEquals("UNAUTHENTICATED", error["extensions"]!!.jsonObject.str("code"))
    }

    private fun idToken(
        nonce: String,
        sub: String = "google-sub-1",
        email: String = "someone@example.com",
    ) = googleIdToken(key.second, subject = sub, nonce = nonce, email = email)

    private fun users() = transaction(db) { AppUserTable.selectAll().count() }

    @Test
    fun signInIssuesASessionForTheGoogleSub() =
        authApp {
            val session = client.signIn(idToken(client.nonce())).payload("signInWithGoogle")
            val userId = tokens.verifyAccess(session.str("accessToken"))
            assertEquals(1, users())
            assertEquals(TEST_NOW.plus(ACCESS_TOKEN_TTL).toString(), session.str("accessTokenExpiresAt"))
            assertEquals(TEST_NOW.plus(REFRESH_TOKEN_TTL).toString(), session.str("refreshTokenExpiresAt"))
            assertNotEquals("", userId.toString())
        }

    @Test
    fun theSameSubIsTheSameUserWhateverTheEmail() =
        authApp {
            val first = client.signIn(idToken(client.nonce(), email = "a@example.com")).payload("signInWithGoogle")
            val second = client.signIn(idToken(client.nonce(), email = "b@example.com")).payload("signInWithGoogle")
            assertEquals(tokens.verifyAccess(first.str("accessToken")), tokens.verifyAccess(second.str("accessToken")))
            assertEquals(1, users())
        }

    @Test
    fun differentSubsAreDifferentUsers() =
        authApp {
            val a = client.signIn(idToken(client.nonce(), sub = "sub-a")).payload("signInWithGoogle")
            val b = client.signIn(idToken(client.nonce(), sub = "sub-b")).payload("signInWithGoogle")
            assertNotEquals(tokens.verifyAccess(a.str("accessToken")), tokens.verifyAccess(b.str("accessToken")))
        }

    @Test
    fun anUnknownNonceIsRejected() =
        authApp {
            client.signIn(idToken("never-issued")).assertUnauthenticated()
            assertEquals(0, users())
        }

    @Test
    fun aNonceWorksOnce() =
        authApp {
            val token = idToken(client.nonce())
            assertNull(client.signIn(token)["errors"])
            client.signIn(token).assertUnauthenticated()
        }

    @Test
    fun anExpiredNonceIsRejected() =
        authApp {
            val token = idToken(client.nonce())
            clock.now = TEST_NOW.plus(Duration.ofMinutes(6))
            val fresh =
                googleIdToken(
                    key.second,
                    nonce = nonceOf(token),
                    expiresAt = clock.now.plusSeconds(3600),
                    issuedAt = clock.now,
                )
            client.signIn(fresh).assertUnauthenticated()
        }

    @Test
    fun aBadGoogleTokenDoesNotBurnTheNonce() =
        authApp {
            val nonce = client.nonce()
            client.signIn(googleIdToken(newRsaKey().second, nonce = nonce)).assertUnauthenticated()
            assertNull(client.signIn(idToken(nonce))["errors"])
        }

    @Test
    fun refreshRotatesTheToken() =
        authApp {
            val first = client.signIn(idToken(client.nonce())).payload("signInWithGoogle")
            val second = client.refresh(first.str("refreshToken")).payload("refreshSession")
            assertNotEquals(first.str("refreshToken"), second.str("refreshToken"))
            assertEquals(
                tokens.verifyAccess(first.str("accessToken")),
                tokens.verifyAccess(second.str("accessToken")),
            )
            assertNull(client.refresh(second.str("refreshToken"))["errors"])
        }

    @Test
    fun reusingARotatedTokenRevokesTheFamily() =
        authApp {
            val first = client.signIn(idToken(client.nonce())).payload("signInWithGoogle")
            val second = client.refresh(first.str("refreshToken")).payload("refreshSession")

            client.refresh(first.str("refreshToken")).assertUnauthenticated()
            client.refresh(second.str("refreshToken")).assertUnauthenticated()
            val live =
                transaction(db) { RefreshTokenTable.selectAll().count { it[RefreshTokenTable.revokedAt] == null } }
            assertEquals(0, live)
        }

    @Test
    fun signOutRevokesTheWholeFamily() =
        authApp {
            val first = client.signIn(idToken(client.nonce())).payload("signInWithGoogle")
            val second = client.refresh(first.str("refreshToken")).payload("refreshSession")

            val result = client.signOut(second.str("refreshToken"))

            assertNull(result["errors"])
            assertEquals(true, result["data"]!!.jsonObject["signOut"]!!.jsonPrimitive.boolean)
            client.refresh(second.str("refreshToken")).assertUnauthenticated()
            client.refresh(first.str("refreshToken")).assertUnauthenticated()
        }

    @Test
    fun signOutLeavesOtherSessionsAlone() =
        authApp {
            val phone = client.signIn(idToken(client.nonce())).payload("signInWithGoogle")
            val tablet = client.signIn(idToken(client.nonce())).payload("signInWithGoogle")

            client.signOut(phone.str("refreshToken"))

            assertNull(client.refresh(tablet.str("refreshToken"))["errors"])
        }

    @Test
    fun signOutIsNotAnErrorForRepeatedUnknownOrMalformedTokens() =
        authApp {
            val session = client.signIn(idToken(client.nonce())).payload("signInWithGoogle")
            val unknown = "A".repeat(session.str("refreshToken").length)

            listOf(session.str("refreshToken"), session.str("refreshToken"), unknown, "short").forEach { token ->
                val result = client.signOut(token)
                assertNull(result["errors"])
                assertEquals(true, result["data"]!!.jsonObject["signOut"]!!.jsonPrimitive.boolean)
            }
        }

    @Test
    fun deleteAccountRemovesTheUserEntriesTombstonesAndSessions() =
        authApp {
            val mine = client.signIn(idToken(client.nonce(), sub = "sub-mine")).payload("signInWithGoogle")
            val other = client.signIn(idToken(client.nonce(), sub = "sub-other")).payload("signInWithGoogle")
            val mineId = userId("sub-mine")
            val otherId = userId("sub-other")
            seedEntries(mineId, 1)
            seedEntries(otherId, 3)
            listOf("app_user" to 1L, "list_entry" to 2L, "refresh_token" to 1L).forEach { (table, rows) ->
                assertEquals(rows, rowsFor(table, mineId))
            }

            val result = client.deleteAccount(idToken(client.deleteNonce(), sub = "sub-mine"))

            assertNull(result["errors"])
            assertEquals(true, result["data"]!!.jsonObject["deleteAccount"]!!.jsonPrimitive.boolean)
            listOf("app_user", "list_entry", "refresh_token").forEach { assertEquals(0L, rowsFor(it, mineId)) }
            listOf("app_user" to 1L, "list_entry" to 2L, "refresh_token" to 1L).forEach { (table, rows) ->
                assertEquals(rows, rowsFor(table, otherId))
            }
            client.refresh(mine.str("refreshToken")).assertUnauthenticated()
            assertNull(client.refresh(other.str("refreshToken"))["errors"])
        }

    @Test
    fun deleteAccountTwiceIsNotAnError() =
        authApp {
            client.signIn(idToken(client.nonce(), sub = "sub-mine"))
            client.deleteAccount(idToken(client.deleteNonce(), sub = "sub-mine"))

            val again = client.deleteAccount(idToken(client.deleteNonce(), sub = "sub-mine"))

            assertNull(again["errors"])
            assertEquals(0, users())
        }

    @Test
    fun deleteAccountRefusesASignInNonce() =
        authApp {
            client.signIn(idToken(client.nonce(), sub = "sub-mine"))

            client.deleteAccount(idToken(client.nonce(), sub = "sub-mine")).assertUnauthenticated()

            assertEquals(1, users())
        }

    @Test
    fun signInRefusesADeleteNonce() =
        authApp {
            client.signIn(idToken(client.deleteNonce(), sub = "sub-mine")).assertUnauthenticated()

            assertEquals(0, users())
        }

    @Test
    fun aDeleteTokenCannotBeUsedTwice() =
        authApp {
            client.signIn(idToken(client.nonce(), sub = "sub-mine"))
            val token = idToken(client.deleteNonce(), sub = "sub-mine")
            assertNull(client.deleteAccount(token)["errors"])
            client.signIn(idToken(client.nonce(), sub = "sub-mine"))

            client.deleteAccount(token).assertUnauthenticated()

            assertEquals(1, users())
        }

    @Test
    fun deleteAccountWithAForgedTokenDeletesNothing() =
        authApp {
            client.signIn(idToken(client.nonce(), sub = "sub-mine"))
            val forged =
                googleIdToken(
                    newRsaKey().second,
                    subject = "sub-mine",
                    nonce = client.deleteNonce(),
                    email = "x@example.com",
                )

            client.deleteAccount(forged).assertUnauthenticated()

            assertEquals(1, users())
        }

    @Test
    fun anExpiredRefreshTokenIsRejected() =
        authApp {
            val session = client.signIn(idToken(client.nonce())).payload("signInWithGoogle")
            clock.now = TEST_NOW.plus(REFRESH_TOKEN_TTL).plusSeconds(1)
            client.refresh(session.str("refreshToken")).assertUnauthenticated()
        }

    @Test
    fun anUnknownRefreshTokenIsRejected() =
        authApp {
            client.refresh(tokens.newRefreshToken().token).assertUnauthenticated()
        }

    @Test
    fun aMalformedRefreshTokenIsRejectedBeforeTheDatabase() =
        authApp {
            val response = client.refresh("short")
            assertTrue(response["errors"] != null)
        }

    @Test
    fun withoutAuthConfigSignInIsNotAvailable() =
        testApplication {
            application { module(db, clock, null) }
            val response = client.gql("mutation { signInNonce }")
            assertTrue(
                response["errors"]!!
                    .jsonArray
                    .single()
                    .jsonObject
                    .str("message")
                    .contains("not configured"),
            )
        }

    private fun nonceOf(idToken: String): String =
        com.auth0.jwt.JWT
            .decode(idToken)
            .getClaim("nonce")
            .asString()
}
