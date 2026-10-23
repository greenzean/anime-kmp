package com.greenzean.animekmp.shared.sync.data

import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.sync.domain.AccountLibraryDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightAccountLibraryDataSource(
    private val db: AnimeDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AccountLibraryDataSource {
    private val listEntries get() = db.listEntryQueries

    override suspend fun pendingCount(): Int =
        withContext(dispatcher) { listEntries.countPending().executeAsOne().toInt() }

    override suspend fun activeCount(): Int =
        withContext(dispatcher) { listEntries.countActive().executeAsOne().toInt() }

    override suspend fun clearLibrary() {
        withContext(dispatcher) {
            db.transaction {
                listEntries.deleteAll()
                db.parkedChangeQueries.deleteAll()
                db.syncCursorQueries.clearCursor()
            }
        }
    }

    override suspend fun detachFromAccount() {
        withContext(dispatcher) {
            db.transaction {
                listEntries.deleteSyncedTombstones()
                listEntries.markAllPending()
                db.parkedChangeQueries.deleteAll()
                db.syncCursorQueries.clearCursor()
            }
        }
    }
}
