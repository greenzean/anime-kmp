package com.greenzean.animekmp.server

import com.greenzean.animekmp.server.routes.healthRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

fun main() {
    val config = AppConfig.fromEnv()
    // Must run before the first logger is created.
    if (config.env == AppEnv.PROD) System.setProperty("logback.configurationFile", "logback-prod.xml")
    embeddedServer(Netty, port = config.port, host = config.host) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(CallLogging)
    install(ContentNegotiation) { json() }
    routing {
        healthRoutes()
    }
}
