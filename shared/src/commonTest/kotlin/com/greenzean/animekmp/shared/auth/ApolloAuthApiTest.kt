package com.greenzean.animekmp.shared.auth

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import com.greenzean.animekmp.shared.auth.data.ApolloAuthApi
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private class CannedEngine(
    private val body: String,
) : HttpEngine {
    val requests = mutableListOf<HttpRequest>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return HttpResponse.Builder(200).body(Buffer().writeUtf8(body)).build()
    }

    override fun close() = Unit
}

class ApolloAuthApiTest {
    private fun api(body: String): Pair<ApolloAuthApi, CannedEngine> {
        val engine = CannedEngine(body)
        val client =
            ApolloClient
                .Builder()
                .serverUrl("http://localhost/graphql")
                .httpEngine(engine)
                .build()
        return ApolloAuthApi(client) to engine
    }

    @Test
    fun nonceIsReturned() =
        runTest {
            val (api, _) = api("""{"data":{"signInNonce":"n-123"}}""")
            assertEquals(Result.Success("n-123"), api.signInNonce())
        }

    @Test
    fun signInReturnsTheSessionAndSendsNoAuthorization() =
        runTest {
            val (api, engine) =
                api(
                    """{"data":{"signInWithGoogle":{"accessToken":"a1",""" +
                        """"accessTokenExpiresAt":"2026-10-22T12:15:00Z","refreshToken":"r1"}}}""",
                )

            val result = api.signInWithGoogle("id-token")

            assertEquals(Result.Success(Session("a1", Instant.parse("2026-10-22T12:15:00Z"), "r1")), result)
            assertEquals(
                emptyList(),
                engine.requests
                    .single()
                    .headers
                    .filter { it.name == "Authorization" },
            )
        }

    @Test
    fun aRejectedIdTokenIsUnauthorized() =
        runTest {
            val (api, _) =
                api(
                    """{"errors":[{"message":"Authentication failed",""" +
                        """"extensions":{"code":"UNAUTHENTICATED"}}],"data":null}""",
                )
            assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), api.signInWithGoogle("bad"))
        }
}
