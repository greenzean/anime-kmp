package com.greenzean.animekmp.server.routes

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val CLIENT_ID_PLACEHOLDER = "{{GOOGLE_CLIENT_ID}}"

private const val CONTENT_SECURITY_POLICY =
    "default-src 'none'; " +
        "script-src 'self' https://accounts.google.com/gsi/client; " +
        "style-src 'self' https://accounts.google.com/gsi/style; " +
        "connect-src 'self' https://accounts.google.com/gsi/; " +
        "frame-src https://accounts.google.com/gsi/; " +
        "frame-ancestors 'none'; base-uri 'none'; form-action 'none'"

private fun resourceText(name: String): String =
    checkNotNull(object {}.javaClass.getResource("/web/$name")) { "Missing resource $name" }.readText()

private fun String.htmlEscaped() =
    replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

private suspend fun ApplicationCall.respondStatic(
    body: String,
    type: ContentType,
) {
    response.headers.append("Content-Security-Policy", CONTENT_SECURITY_POLICY)
    response.headers.append("X-Content-Type-Options", "nosniff")
    response.headers.append("Referrer-Policy", "no-referrer")
    respondText(body, type)
}

fun Route.accountDeletionRoutes(googleClientId: String?) {
    val page =
        resourceText(
            "account-deletion.html",
        ).replace(CLIENT_ID_PLACEHOLDER, (googleClientId ?: "").htmlEscaped())
    val style = resourceText("account-deletion.css")
    val script = resourceText("account-deletion.js")
    get("/account/delete") { call.respondStatic(page, ContentType.Text.Html) }
    get("/account/delete/style.css") { call.respondStatic(style, ContentType.Text.CSS) }
    get("/account/delete/script.js") { call.respondStatic(script, ContentType.Application.JavaScript) }
}
