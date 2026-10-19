package com.greenzean.animekmp.server.db

import com.greenzean.animekmp.server.DatabaseConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.SQLException
import java.time.OffsetDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class MigrationTest {
    private lateinit var container: PostgreSQLContainer
    private lateinit var db: Database

    @BeforeTest
    fun setUp() {
        container = PostgreSQLContainer("postgres:18-alpine").also { it.start() }
        db = connectDatabase(DatabaseConfig(container.jdbcUrl, container.username, container.password))
    }

    @AfterTest
    fun tearDown() {
        container.stop()
    }

    @Test
    fun seedAnimeIsPresent() {
        val titles = transaction(db) { AnimeTable.selectAll().map { it[AnimeTable.title] } }
        assertEquals(4, titles.size)
        assert("Cowboy Bebop" in titles)
    }

    @Test
    fun malIdIsUnique() {
        assertFailsWith<SQLException> {
            transaction(db) {
                AnimeTable.insert {
                    it[malId] = 1
                    it[title] = "Duplicate"
                }
            }
        }
    }

    @Test
    fun listEntrySupportsTombstone() {
        val entryId = Uuid.random()
        transaction(db) {
            val animeId = AnimeTable.selectAll().where { AnimeTable.malId eq 1 }.single()[AnimeTable.id]
            ListEntryTable.insert {
                it[id] = entryId
                it[userId] = Uuid.random()
                it[this.animeId] = animeId
                it[status] = "WATCHING"
                it[updatedAt] = OffsetDateTime.now()
                it[deletedAt] = OffsetDateTime.now()
            }
        }
        val row = transaction(db) { ListEntryTable.selectAll().where { ListEntryTable.id eq entryId }.single() }
        assertNotNull(row[ListEntryTable.deletedAt])
    }

    @Test
    fun invalidStatusRejected() {
        assertFailsWith<SQLException> {
            transaction(db) {
                val animeId = AnimeTable.selectAll().first()[AnimeTable.id]
                ListEntryTable.insert {
                    it[id] = Uuid.random()
                    it[userId] = Uuid.random()
                    it[this.animeId] = animeId
                    it[status] = "BOGUS"
                    it[updatedAt] = OffsetDateTime.now()
                }
            }
        }
    }

    @Test
    fun deletedAtDefaultsToNull() {
        val entryId = Uuid.random()
        transaction(db) {
            ListEntryTable.insert {
                it[id] = entryId
                it[userId] = Uuid.random()
                it[animeId] = AnimeTable.selectAll().first()[AnimeTable.id]
                it[status] = "COMPLETED"
                it[updatedAt] = OffsetDateTime.now()
            }
        }
        val row = transaction(db) { ListEntryTable.selectAll().where { ListEntryTable.id eq entryId }.single() }
        assertNull(row[ListEntryTable.deletedAt])
    }

    @Test
    fun v4BackfillsServerSeqForExistingRows() {
        PostgreSQLContainer("postgres:18-alpine").use { fresh ->
            fresh.start()
            val ds = createDataSource(DatabaseConfig(fresh.jdbcUrl, fresh.username, fresh.password))

            fun flyway(target: String) =
                Flyway
                    .configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .target(target)
                    .load()
            flyway("3").migrate()
            ds.connection.use { c ->
                c.createStatement().use { st ->
                    for (i in 1..3) {
                        st.execute(
                            "INSERT INTO list_entry (id, user_id, anime_id, status, updated_at) VALUES " +
                                "(gen_random_uuid(), gen_random_uuid(), $i, 'WATCHING', now())",
                        )
                    }
                }
            }
            flyway("4").migrate()
            val seqs =
                ds.connection.use { c ->
                    c.createStatement().use { st ->
                        st.executeQuery("SELECT server_seq FROM list_entry ORDER BY server_seq").use { rs ->
                            generateSequence { if (rs.next()) rs.getLong(1) else null }.toList()
                        }
                    }
                }
            assertEquals(3, seqs.size)
            assertEquals(3, seqs.toSet().size)
        }
    }
}
