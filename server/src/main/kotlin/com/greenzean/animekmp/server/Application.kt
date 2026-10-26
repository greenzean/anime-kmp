package com.greenzean.animekmp.server

import com.expediagroup.graphql.server.ktor.GraphQL
import com.greenzean.animekmp.server.auth.AuthRepository
import com.greenzean.animekmp.server.auth.AuthService
import com.greenzean.animekmp.server.auth.GoogleIdTokenVerifier
import com.greenzean.animekmp.server.auth.SessionTokens
import com.greenzean.animekmp.server.db.connectDatabase
import com.greenzean.animekmp.server.graphql.GraphQLSchemaDefinition
import com.greenzean.animekmp.server.graphql.SyncSchemaHooks
import com.greenzean.animekmp.server.graphql.auth.AuthContextFactory
import com.greenzean.animekmp.server.routes.accountDeletionRoutes
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
    if (config.env == AppEnv.PROD) System.setProperty("logback.configurationFile", "logback-prod.xml")
    val database = config.database?.let { connectDatabase(it) }
    val tokens = config.auth?.let { SessionTokens(it.jwtSecret) }
    val auth = config.auth?.let { authService(it, database, checkNotNull(tokens)) }
    embeddedServer(Netty, port = config.port, host = config.host) {
        module(database, auth = auth, tokens = tokens, googleClientId = config.auth?.googleClientId)
    }.start(wait = true)
}

private fun authService(
    config: AuthConfig,
    database: Database?,
    tokens: SessionTokens,
): AuthService {
    checkNotNull(database) { "Sign-in needs a database: set DATABASE_URL" }
    return AuthService(
        verifier = GoogleIdTokenVerifier(config.googleClientId, GoogleIdTokenVerifier.googleKeys()),
        tokens = tokens,
        repository = AuthRepository(database),
    )
}

fun Application.module(
    database: Database? = null,
    clock: Clock = Clock.systemUTC(),
    auth: AuthService? = null,
    tokens: SessionTokens? = null,
    googleClientId: String? = null,
) {
    val sync = SyncRepository(database, clock)
    install(CallLogging)
    install(GraphQL) {
        schema {
            packages = GraphQLSchemaDefinition.packages
            queries = GraphQLSchemaDefinition.queries(sync)
            mutations = GraphQLSchemaDefinition.mutations(sync, auth)
            hooks = SyncSchemaHooks
        }
        server {
            contextFactory = AuthContextFactory(tokens)
        }
    }
    routing {
        route("") {
            install(ContentNegotiation) { json() }
            healthRoutes()
            accountDeletionRoutes(googleClientId)
        }
        graphQLRoutes()
    }
}
