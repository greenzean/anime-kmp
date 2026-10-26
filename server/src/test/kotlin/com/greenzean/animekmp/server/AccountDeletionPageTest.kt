package com.greenzean.animekmp.server

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountDeletionPageTest {
    private fun HttpResponse.assertContentType(type: ContentType) =
        assertTrue(headers[HttpHeaders.ContentType]!!.startsWith(type.toString()))

    private fun HttpResponse.assertSecurityHeaders() {
        assertTrue(headers["Content-Security-Policy"]!!.contains("frame-ancestors 'none'"))
        assertEquals("nosniff", headers["X-Content-Type-Options"])
        assertEquals("no-referrer", headers["Referrer-Policy"])
    }

    @Test
    fun pageIsPublicHtmlWithOnlyExternalScripts() =
        testApplication {
            application { module(googleClientId = "web-client-1") }
            val response = client.get("/account/delete")
            val body = response.bodyAsText()
            assertEquals(HttpStatusCode.OK, response.status)
            response.assertContentType(ContentType.Text.Html)
            response.assertSecurityHeaders()
            val scripts = Regex("<script[^>]*>").findAll(body).map { it.value }.toList()
            assertTrue(scripts.isNotEmpty() && scripts.all { it.contains("src=") })
            assertTrue(body.contains("""data-client-id="web-client-1""""))
            assertTrue(body.contains("animekmp://delete-account"))
            assertFalse(body.contains("{{"))
        }

    @Test
    fun pageWithoutSignInConfigHasAnEmptyClientId() =
        testApplication {
            application { module() }
            assertTrue(client.get("/account/delete").bodyAsText().contains("""data-client-id="""""))
        }

    @Test
    fun stylesheetAndScriptAreServedWithSecurityHeaders() =
        testApplication {
            application { module() }
            val style = client.get("/account/delete/style.css")
            assertEquals(HttpStatusCode.OK, style.status)
            style.assertContentType(ContentType.Text.CSS)
            style.assertSecurityHeaders()
            val script = client.get("/account/delete/script.js")
            assertEquals(HttpStatusCode.OK, script.status)
            script.assertContentType(ContentType.Application.JavaScript)
            script.assertSecurityHeaders()
        }
}
