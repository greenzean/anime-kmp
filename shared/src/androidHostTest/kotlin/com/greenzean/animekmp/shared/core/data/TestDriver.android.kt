package com.greenzean.animekmp.shared.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import java.util.Properties

actual fun createTestDriver(): SqlDriver =
    JdbcSqliteDriver(
        JdbcSqliteDriver.IN_MEMORY,
        Properties().apply {
            put("foreign_keys", "true")
        },
    ).also { AnimeDatabase.Schema.create(it) }
