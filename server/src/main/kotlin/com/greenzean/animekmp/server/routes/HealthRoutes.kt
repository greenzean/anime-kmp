package com.greenzean.animekmp.server.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes() {
    get("/health") {
        call.respond(HealthResponse(status = "ok"))
    }
}
