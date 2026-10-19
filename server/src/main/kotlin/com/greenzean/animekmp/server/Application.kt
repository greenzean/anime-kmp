package com.greenzean.animekmp.server

import com.expediagroup.graphql.server.ktor.GraphQL
import com.greenzean.animekmp.server.db.connectDatabase
import com.greenzean.animekmp.server.graphql.GraphQLSchemaDefinition
import com.greenzean.animekmp.server.graphql.SyncSchemaHooks
import com.greenzean.animekmp.server.routes.graphQLRoutes
import com.greenzean.animekmp.server.routes.healthRoutes
import com.greenzean.animekmp.server.sync.SyncRepository
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

fun main() {
    val config = AppConfig.fromEnv()
    // Must run before the first logger is created.
    if (config.env == AppEnv.PROD) System.setProperty("logback.configurationFile", "logback-prod.xml")
    val database = config.database?.let { connectDatabase(it) }
    embeddedServer(Netty, port = config.port, host = config.host) {
        module(database)
    }.start(wait = true)
}

fun Application.module(
    database: Database? = null,
    clock: Clock = Clock.systemUTC(),
) {
    val sync = SyncRepository(database, clock)
    install(CallLogging)
    install(GraphQL) {
        schema {
            packages = GraphQLSchemaDefinition.packages
            queries = GraphQLSchemaDefinition.queries(sync)
            mutations = GraphQLSchemaDefinition.mutations(sync)
            hooks = SyncSchemaHooks
        }
    }
    routing {
        // graphQLPostRoute installs its own Jackson ContentNegotiation; an app-wide kotlinx one would break it.
        route("") {
            install(ContentNegotiation) { json() }
            healthRoutes()
        }
        graphQLRoutes()
    }
}
