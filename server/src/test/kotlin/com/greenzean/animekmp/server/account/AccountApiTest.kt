package com.greenzean.animekmp.server.account

import com.greenzean.animekmp.server.sync.SyncApiTestBase
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountApiTest : SyncApiTestBase() {
    private fun JsonObject.platforms(field: String): List<String> =
        this["data"]!!
            .jsonObject[field]!!
            .jsonObject["linkedPlatforms"]!!
            .jsonArray
            .map { it.jsonPrimitive.content }

    private fun JsonObject.assertUnauthenticated() {
        val error = this["errors"]!!.jsonArray.first().jsonObject
        assertEquals("UNAUTHENTICATED", error["extensions"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    private fun rows(): Long =
        transaction(db) {
            var count = 0L
            exec("SELECT count(*) FROM linked_platform") { rs ->
                rs.next()
                count = rs.getLong(1)
            }
            count
        }

    private val accountQuery = "{ account { linkedPlatforms } }"
    private val link = "mutation { linkPlatform(platform: MAL) { linkedPlatforms } }"
    private val unlink = "mutation { unlinkPlatform(platform: MAL) { linkedPlatforms } }"

    @Test
    fun anAccountStartsWithNothingLinked() =
        sync {
            assertEquals(emptyList(), client.gql(accountQuery).platforms("account"))
        }

    @Test
    fun linkingMalShowsUpInTheAccount() =
        sync {
            assertEquals(listOf("MAL"), client.gql(link).platforms("linkPlatform"))
            assertEquals(listOf("MAL"), client.gql(accountQuery).platforms("account"))
        }

    @Test
    fun linkingTwiceIsNotAnError() =
        sync {
            client.gql(link)

            val again = client.gql(link)

            assertNull(again["errors"])
            assertEquals(listOf("MAL"), again.platforms("linkPlatform"))
            assertEquals(1L, rows())
        }

    @Test
    fun unlinkingClearsTheLink() =
        sync {
            client.gql(link)

            assertEquals(emptyList(), client.gql(unlink).platforms("unlinkPlatform"))
            assertEquals(emptyList(), client.gql(accountQuery).platforms("account"))
            assertEquals(0L, rows())
        }

    @Test
    fun unlinkingWhenNotLinkedIsNotAnError() =
        sync {
            val result = client.gql(unlink)

            assertNull(result["errors"])
            assertEquals(emptyList(), result.platforms("unlinkPlatform"))
        }

    @Test
    fun linksArePerUser() =
        sync {
            client.gql(link, bearer(userA))

            assertEquals(emptyList(), client.gql(accountQuery, bearer(userB)).platforms("account"))
            client.gql(unlink, bearer(userB))
            assertEquals(listOf("MAL"), client.gql(accountQuery, bearer(userA)).platforms("account"))
        }

    @Test
    fun theServerStoresOnlyThePlatformName() =
        sync {
            client.gql(link)

            val columns =
                transaction(db) {
                    exec(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'linked_platform'",
                    ) { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
                }
            assertEquals(setOf("user_id", "platform"), columns?.toSet())
        }

    @Test
    fun anUnknownPlatformIsRejected() =
        sync {
            val result = client.gql("mutation { linkPlatform(platform: ANILIST) { linkedPlatforms } }")

            assertNotNull(result["errors"])
            assertEquals(0L, rows())
        }

    @Test
    fun accountRequiresAuthentication() =
        sync {
            client.gql(accountQuery, token = null).assertUnauthenticated()
        }

    @Test
    fun linkRequiresAuthentication() =
        sync {
            client.gql(link, token = null).assertUnauthenticated()
            assertEquals(0L, rows())
        }

    @Test
    fun unlinkRequiresAuthentication() =
        sync {
            client.gql(link)

            client.gql(unlink, token = null).assertUnauthenticated()
            assertTrue(rows() == 1L)
        }

    @Test
    fun linkingForADeletedUserWithAStillValidTokenIsUnauthenticated() =
        sync {
            val token = bearer(userA)
            transaction(db) { exec("DELETE FROM app_user WHERE id = '$userA'") }

            client.gql(link, token).assertUnauthenticated()
            assertEquals(0L, rows())
        }

    @Test
    fun theAccountForADeletedUserWithAStillValidTokenIsUnauthenticated() =
        sync {
            val token = bearer(userA)
            transaction(db) { exec("DELETE FROM app_user WHERE id = '$userA'") }

            client.gql(accountQuery, token).assertUnauthenticated()
        }

    @Test
    fun unlinkingForADeletedUserWithAStillValidTokenIsUnauthenticated() =
        sync {
            val token = bearer(userA)
            client.gql(link, token)
            transaction(db) { exec("DELETE FROM app_user WHERE id = '$userA'") }

            client.gql(unlink, token).assertUnauthenticated()
        }
}
