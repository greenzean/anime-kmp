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
        val config = AppConfig.fromEnv(mapOf("PORT" to "9090", "HOST" to "127.0.0.1", "APP_ENV" to "PROD"))
        assertEquals(AppConfig("127.0.0.1", 9090, AppEnv.PROD), config)
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
}
