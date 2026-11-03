package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.importer.domain.OnMalLocalDataSource
import com.greenzean.animekmp.shared.mal.domain.MalImportProgressStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightMalImportProgressStore(
    private val db: AnimeDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MalImportProgressStore {
    override suspend fun offset(): Int =
        withContext(dispatcher) {
            db.malImportProgressQueries
                .selectOffset()
                .executeAsOneOrNull()
                ?.toInt() ?: 0
        }

    override suspend fun setOffset(offset: Int) {
        withContext(dispatcher) {
            db.malImportProgressQueries.saveOffset(offset.toLong())
        }
    }

    override suspend fun clear() {
        withContext(dispatcher) {
            db.malImportProgressQueries.clearOffset()
        }
    }
}

class SqlDelightOnMalDataSource(
    private val db: AnimeDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : OnMalLocalDataSource {
    override suspend fun markOnMal(malId: Int) {
        withContext(dispatcher) {
            db.listEntryQueries.markOnMal(malId.toLong())
        }
    }

    override suspend fun activeOnMalIds(): List<Int> =
        withContext(dispatcher) {
            db.listEntryQueries
                .selectActiveOnMalIds()
                .executeAsList()
                .map { it.toInt() }
        }
}
