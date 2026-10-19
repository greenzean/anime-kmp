package com.greenzean.animekmp.server.db

import com.greenzean.animekmp.server.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

private const val MAX_POOL_SIZE = 10

/** Opens the pool, applies pending Flyway migrations and connects Exposed. Flyway owns the schema (ADR 0005). */
fun connectDatabase(config: DatabaseConfig): Database {
    val dataSource = createDataSource(config)
    migrate(dataSource)
    return Database.connect(dataSource)
}

fun createDataSource(config: DatabaseConfig): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = MAX_POOL_SIZE
        },
    )

fun migrate(dataSource: DataSource) {
    Flyway
        .configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()
}
