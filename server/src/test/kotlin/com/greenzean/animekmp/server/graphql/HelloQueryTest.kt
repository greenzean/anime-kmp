package com.greenzean.animekmp.server.graphql

import com.greenzean.animekmp.server.module
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HelloQueryTest {
    @Test
    fun helloReturnsWorld() =
        testApplication {
            application { module() }
            val response =
                client.post("/graphql") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"query":"{ hello }"}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"data":{"hello":"world"}}""", response.bodyAsText())
        }
}
