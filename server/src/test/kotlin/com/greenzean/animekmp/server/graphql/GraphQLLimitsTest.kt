package com.greenzean.animekmp.server.graphql

import com.greenzean.animekmp.server.Limits
import com.greenzean.animekmp.server.auth.SessionTokens
import com.greenzean.animekmp.server.module
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphQLLimitsTest {
    private val tokens = SessionTokens("0123456789abcdef0123456789abcdef")

    private fun app(
        limits: Limits = Limits(),
        introspection: Boolean = true,
        trustForwardedHeaders: Boolean = false,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            module(
                tokens = tokens,
                limits = limits,
                introspectionEnabled = introspection,
                trustForwardedHeaders = trustForwardedHeaders,
            )
        }
        block()
    }

    private suspend fun HttpClient.gqlFrom(forwardedFor: String): HttpResponse =
        post("/graphql") {
            contentType(ContentType.Application.Json)
            header("X-Forwarded-For", forwardedFor)
            setBody("""{"query":"{ hello }"}""")
        }

    private suspend fun HttpClient.gql(
        query: String,
        token: String? = null,
    ): HttpResponse =
        post("/graphql") {
            contentType(ContentType.Application.Json)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody("""{"query":"$query"}""")
        }

    private fun nested(levels: Int) =
        "{ __schema { types { fields { type { " + "ofType { ".repeat(levels) + "name" + " }".repeat(levels) +
            " } } } } }"

    @Test
    fun queryWithinDepthLimitRuns() =
        app(Limits(maxQueryDepth = 8)) {
            val body = client.gql(nested(1)).bodyAsText()
            assertTrue(body.contains("\"data\""), body)
            assertFalse(body.contains("\"errors\""), body)
        }

    @Test
    fun queryOverDepthLimitIsRejected() =
        app(Limits(maxQueryDepth = 8)) {
            val body = client.gql(nested(6)).bodyAsText()
            assertTrue(body.contains("\"errors\""), body)
            assertFalse(body.contains("\"types\""), body)
        }

    @Test
    fun defaultDepthLimitIsTen() =
        app {
            assertEquals(10, Limits().maxQueryDepth)
            assertTrue(client.gql(nested(2)).bodyAsText().contains("\"data\""))
            assertTrue(client.gql(nested(8)).bodyAsText().contains("\"errors\""))
        }

    @Test
    fun queryWithinComplexityLimitRuns() =
        app(Limits(maxQueryComplexity = 3)) {
            assertEquals("""{"data":{"hello":"world"}}""", client.gql("{ hello }").bodyAsText())
        }

    @Test
    fun queryOverComplexityLimitIsRejected() =
        app(Limits(maxQueryComplexity = 3)) {
            val body = client.gql("{ a: hello b: hello c: hello d: hello e: hello }").bodyAsText()
            assertTrue(body.contains("\"errors\""), body)
            assertFalse(body.contains("world"), body)
        }

    @Test
    fun bodyOverTheLimitIsRejected() =
        app(Limits(maxBodyBytes = 64)) {
            val response = client.gql("{ hello }" + " ".repeat(200))
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun bodyWithinTheLimitRuns() =
        app(Limits(maxBodyBytes = 256)) {
            assertEquals(HttpStatusCode.OK, client.gql("{ hello }").status)
        }

    @Test
    fun ipOverTheRateLimitGets429() =
        app(Limits(requestsPerIp = 3, requestsPerUser = 100)) {
            repeat(3) { assertEquals(HttpStatusCode.OK, client.gql("{ hello }").status) }
            assertEquals(HttpStatusCode.TooManyRequests, client.gql("{ hello }").status)
        }

    @Test
    fun forwardedClientAddressesAreLimitedSeparatelyWhenTrusted() =
        app(Limits(requestsPerIp = 2, requestsPerUser = 100), trustForwardedHeaders = true) {
            repeat(2) { assertEquals(HttpStatusCode.OK, client.gqlFrom("203.0.113.1").status) }
            assertEquals(HttpStatusCode.TooManyRequests, client.gqlFrom("203.0.113.1").status)
            assertEquals(HttpStatusCode.OK, client.gqlFrom("203.0.113.2").status)
        }

    @Test
    fun forwardedClientAddressIsIgnoredWhenNotTrusted() =
        app(Limits(requestsPerIp = 2, requestsPerUser = 100)) {
            assertEquals(HttpStatusCode.OK, client.gqlFrom("203.0.113.1").status)
            assertEquals(HttpStatusCode.OK, client.gqlFrom("203.0.113.2").status)
            assertEquals(HttpStatusCode.TooManyRequests, client.gqlFrom("203.0.113.3").status)
        }

    @Test
    fun spoofedFirstForwardedAddressDoesNotEscapeTheLimitWhenTrusted() =
        app(Limits(requestsPerIp = 2, requestsPerUser = 100), trustForwardedHeaders = true) {
            assertEquals(HttpStatusCode.OK, client.gqlFrom("198.51.100.1, 203.0.113.9").status)
            assertEquals(HttpStatusCode.OK, client.gqlFrom("198.51.100.2, 203.0.113.9").status)
            assertEquals(HttpStatusCode.TooManyRequests, client.gqlFrom("198.51.100.3, 203.0.113.9").status)
        }

    @Test
    fun userOverTheRateLimitGets429AndOtherUsersAreNotAffected() =
        app(Limits(requestsPerIp = 100, requestsPerUser = 2)) {
            val alice = tokens.issueAccess(UUID.randomUUID()).token
            val bob = tokens.issueAccess(UUID.randomUUID()).token
            repeat(2) { assertEquals(HttpStatusCode.OK, client.gql("{ hello }", alice).status) }
            assertEquals(HttpStatusCode.TooManyRequests, client.gql("{ hello }", alice).status)
            assertEquals(HttpStatusCode.OK, client.gql("{ hello }", bob).status)
        }

    @Test
    fun introspectionOffRejectsSchemaQueriesAndHidesGraphiql() =
        app(introspection = false) {
            val body = client.gql("{ __schema { queryType { name } } }").bodyAsText()
            assertTrue(body.contains("\"errors\""), body)
            assertFalse(body.contains("\"queryType\""), body)
            assertEquals(HttpStatusCode.NotFound, client.get("/graphiql").status)
        }

    @Test
    fun introspectionOnAnswersSchemaQueriesAndServesGraphiql() =
        app(introspection = true) {
            val body = client.gql("{ __schema { queryType { name } } }").bodyAsText()
            assertTrue(body.contains("\"queryType\""), body)
            assertEquals(HttpStatusCode.OK, client.get("/graphiql").status)
        }

    @Test
    fun aliasedAuthMutationsAreChargedPerAlias() =
        app {
            val five = (1..5).joinToString(" ") { "n$it: signInNonce" }
            val three = (1..3).joinToString(" ") { "n$it: signInNonce" }
            assertTrue(client.gql("mutation { $five }").bodyAsText().contains("complexity"))
            assertFalse(client.gql("mutation { $three }").bodyAsText().contains("complexity"))
        }

    @Test
    fun aliasedPushesAreChargedBySize() =
        app {
            val id = "00000000-0000-0000-0000-000000000001"
            val change =
                """{"id":"$id","malId":1,"status":"WATCHING","progress":0,"score":null,""" +
                    """"updatedAt":"2026-10-19T12:00:00Z","deletedAt":null}"""
            val variables = (1..500).joinToString(",", prefix = """{"c":[""", postfix = "]}") { change }
            val query =
                "mutation(${'$'}c:[ListEntryChangeInput!]!){a:pushChanges(changes:${'$'}c){results{id}} " +
                    "b:pushChanges(changes:${'$'}c){results{id}}}"
            val body =
                client
                    .post("/graphql") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"query":"$query","variables":$variables}""")
                    }.bodyAsText()
            assertTrue(body.contains("complexity"), body.take(300))
        }

    @Test
    fun arrayOfOperationsIsNotRunAsABatch() =
        app {
            val response =
                client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody("""[{"query":"{ hello }"},{"query":"{ hello }"}]""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertFalse(response.bodyAsText().contains("world"), response.bodyAsText())
        }

    @Test
    fun chunkedBodyOverTheLimitIsRejected() =
        app(Limits(maxBodyBytes = 1024)) {
            val response =
                client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        object : OutgoingContent.WriteChannelContent() {
                            override suspend fun writeTo(channel: ByteWriteChannel) {
                                channel.writeFully("""{"query":"{ hello }","pad":"""".toByteArray())
                                repeat(8) { channel.writeFully(ByteArray(1024) { 'a'.code.toByte() }) }
                                channel.writeFully("\"}".toByteArray())
                            }
                        },
                    )
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun typeIntrospectionIsRejectedWhenOff() =
        app(introspection = false) {
            val body = client.gql("{ __type(name: \\\"Query\\\") { name } }").bodyAsText()
            assertTrue(body.contains("\"errors\""), body)
            assertFalse(body.contains("\"name\":\"Query\""), body)
        }

    @Test
    fun anonymousTrafficDoesNotLockOutASignedInUser() =
        app(Limits(requestsPerIp = 2, requestsPerUser = 100)) {
            val user = tokens.issueAccess(UUID.randomUUID()).token
            repeat(2) { client.gql("{ hello }") }
            assertEquals(HttpStatusCode.TooManyRequests, client.gql("{ hello }").status)
            assertEquals(HttpStatusCode.OK, client.gql("{ hello }", user).status)
        }

    @Test
    fun anonymousRequestsGetTheFullPerIpBudgetByDefault() =
        app {
            repeat(Limits().requestsPerUser + 1) { assertEquals(HttpStatusCode.OK, client.gql("{ hello }").status) }
        }

    @Test
    fun introspectionOffStillAllowsTypename() =
        app(introspection = false) {
            val body = client.gql("{ __typename hello }").bodyAsText()
            assertTrue(body.contains("world"), body)
        }
}
