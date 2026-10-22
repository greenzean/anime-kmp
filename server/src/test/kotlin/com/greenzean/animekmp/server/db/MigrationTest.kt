package com.greenzean.animekmp.server.db

import com.greenzean.animekmp.server.DatabaseConfig
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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
    fun devCatalogIsGoneAndAnimeHasNoTitle() {
        assertEquals(0, transaction(db) { AnimeTable.selectAll().count() }.toInt())
        val columns =
            transaction(db) {
                exec("SELECT column_name FROM information_schema.columns WHERE table_name = 'anime'") { rs ->
                    generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
                }
            }
        assertEquals(setOf("id", "mal_id"), columns?.toSet())
    }

    @Test
    fun malIdIsUnique() {
        transaction(db) { AnimeTable.insert { it[malId] = 7 } }
        assertFailsWith<SQLException> {
            transaction(db) { AnimeTable.insert { it[malId] = 7 } }
        }
    }

    @Test
    fun malIdMustBePositive() {
        assertFailsWith<SQLException> {
            transaction(db) { AnimeTable.insert { it[malId] = 0 } }
        }
    }

    private var nextMalId = 1

    /** Adds an anime to the ID map and returns its internal id. */
    private fun newAnime(): Long =
        transaction(db) {
            AnimeTable.insert { it[malId] = nextMalId++ }[AnimeTable.id]
        }

    @Test
    fun listEntrySupportsTombstone() {
        val entryId = Uuid.random()
        transaction(db) {
            val animeId = newAnime()
            ListEntryTable.insert {
                it[id] = entryId
                it[userId] = newUser(Uuid.random().toString())
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
                val animeId = newAnime()
                ListEntryTable.insert {
                    it[id] = Uuid.random()
                    it[userId] = newUser(Uuid.random().toString())
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
                it[userId] = newUser(Uuid.random().toString())
                it[animeId] = newAnime()
                it[status] = "COMPLETED"
                it[updatedAt] = OffsetDateTime.now()
            }
        }
        val row = transaction(db) { ListEntryTable.selectAll().where { ListEntryTable.id eq entryId }.single() }
        assertNull(row[ListEntryTable.deletedAt])
    }

    @Test
    fun serverSeqIsFilledAutomaticallyAndIncreases() {
        repeat(2) {
            transaction(db) {
                ListEntryTable.insert {
                    it[id] = Uuid.random()
                    it[userId] = newUser(Uuid.random().toString())
                    it[this.animeId] = newAnime()
                    it[status] = "WATCHING"
                    it[updatedAt] = OffsetDateTime.now()
                }
            }
        }
        val seqs = transaction(db) { ListEntryTable.selectAll().map { it[ListEntryTable.serverSeq] } }
        assertEquals(2, seqs.size)
        assert(seqs[0] < seqs[1])
    }

    private fun newUser(sub: String): Uuid =
        transaction(db) {
            AppUserTable.insert { it[googleSub] = sub }
            AppUserTable.selectAll().where { AppUserTable.googleSub eq sub }.single()[AppUserTable.id]
        }

    private fun insertRefresh(
        user: Uuid,
        hash: ByteArray,
    ) = transaction(db) {
        RefreshTokenTable.insert {
            it[id] = Uuid.random()
            it[userId] = user
            it[familyId] = Uuid.random()
            it[tokenHash] = hash
            it[createdAt] = OffsetDateTime.now()
            it[expiresAt] = OffsetDateTime.now().plusDays(30)
        }
    }

    @Test
    fun googleSubIsUniqueAndNotEmpty() {
        newUser("sub-1")
        assertFailsWith<SQLException> { newUser("sub-1") }
        assertFailsWith<SQLException> { newUser("") }
    }

    @Test
    fun userHasNoEmailColumn() {
        val columns =
            transaction(db) {
                exec("SELECT column_name FROM information_schema.columns WHERE table_name = 'app_user'") { rs ->
                    generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
                }
            }
        assertEquals(setOf("id", "google_sub", "created_at"), columns?.toSet())
    }

    @Test
    fun refreshTokenHashIsUnique() {
        val user = newUser("sub-1")
        insertRefresh(user, byteArrayOf(1, 2, 3))
        assertFailsWith<SQLException> { insertRefresh(user, byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun refreshTokenNeedsAnExistingUser() {
        assertFailsWith<SQLException> { insertRefresh(Uuid.random(), byteArrayOf(9)) }
    }

    private fun insertEntry(user: Uuid) =
        transaction(db) {
            ListEntryTable.insert {
                it[id] = Uuid.random()
                it[userId] = user
                it[animeId] = newAnime()
                it[status] = "WATCHING"
                it[updatedAt] = OffsetDateTime.now()
            }
        }

    @Test
    fun listEntryNeedsAnExistingUser() {
        assertFailsWith<SQLException> { insertEntry(Uuid.random()) }
    }

    @Test
    fun deletingAUserDeletesTheirEntries() {
        val user = newUser("sub-1")
        insertEntry(user)
        transaction(db) { AppUserTable.deleteWhere { AppUserTable.id eq user } }
        assertEquals(0, transaction(db) { ListEntryTable.selectAll().count() }.toInt())
    }
}
