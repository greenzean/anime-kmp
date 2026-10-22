package com.greenzean.animekmp.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppConfigTest {
    @Test
    fun defaults() {
        assertEquals(AppConfig("0.0.0.0", 8080, AppEnv.DEV), AppConfig.fromEnv(emptyMap()))
    }

    @Test
    fun overrides() {
        val auth = AuthConfig("cid", "0123456789abcdef0123456789abcdef")
        val config =
            AppConfig.fromEnv(
                mapOf(
                    "PORT" to "9090",
                    "HOST" to "127.0.0.1",
                    "APP_ENV" to "PROD",
                    "GOOGLE_CLIENT_ID" to auth.googleClientId,
                    "JWT_SECRET" to auth.jwtSecret,
                ),
            )
        assertEquals(AppConfig("127.0.0.1", 9090, AppEnv.PROD, auth = auth), config)
    }

    @Test
    fun databaseConfigRead() {
        val config =
            AppConfig.fromEnv(
                mapOf("DATABASE_URL" to "jdbc:postgresql://h/db", "DATABASE_USER" to "u", "DATABASE_PASSWORD" to "p"),
            )
        assertEquals(DatabaseConfig("jdbc:postgresql://h/db", "u", "p"), config.database)
    }

    @Test
    fun databaseCredentialsRequiredWithUrl() {
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(mapOf("DATABASE_URL" to "jdbc:postgresql://h/db")) }
    }

    @Test
    fun invalidPortRejected() {
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(mapOf("PORT" to "abc")) }
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(mapOf("PORT" to "70000")) }
    }

    @Test
    fun invalidEnvRejected() {
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(mapOf("APP_ENV" to "staging")) }
    }

    private val secret = "0123456789abcdef0123456789abcdef"

    @Test
    fun authConfigRead() {
        val config = AppConfig.fromEnv(mapOf("GOOGLE_CLIENT_ID" to "cid", "JWT_SECRET" to secret))
        assertEquals(AuthConfig("cid", secret), config.auth)
    }

    @Test
    fun blankAuthVariablesCountAsUnset() {
        assertEquals(null, AppConfig.fromEnv(mapOf("GOOGLE_CLIENT_ID" to "", "JWT_SECRET" to "")).auth)
    }

    @Test
    fun authNeedsBothVariables() {
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(mapOf("GOOGLE_CLIENT_ID" to "cid")) }
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(mapOf("JWT_SECRET" to secret)) }
    }

    @Test
    fun shortJwtSecretRejected() {
        assertFailsWith<IllegalStateException> {
            AppConfig.fromEnv(mapOf("GOOGLE_CLIENT_ID" to "cid", "JWT_SECRET" to "too-short"))
        }
    }

    @Test
    fun prodRequiresAuth() {
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(mapOf("APP_ENV" to "prod")) }
    }
}
