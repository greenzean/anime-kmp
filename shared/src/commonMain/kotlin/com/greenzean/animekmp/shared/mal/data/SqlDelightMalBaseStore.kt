package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.domain.MalBaseStore
import com.greenzean.animekmp.shared.mal.domain.MalBaseValues
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightMalBaseStore(
    private val db: AnimeDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MalBaseStore {
    private val queries get() = db.malBaseQueries

    override suspend fun get(malId: Int): MalBaseValues? =
        withContext(dispatcher) {
            queries
                .selectByMalId(malId.toLong())
                .executeAsOneOrNull()
                ?.let { row ->
                    MalBaseValues(
                        status = WatchStatus.valueOf(row.status),
                        progress = row.progress.toInt(),
                        score = row.score?.toInt(),
                    )
                }
        }

    override suspend fun save(
        malId: Int,
        values: MalBaseValues,
    ) {
        withContext(dispatcher) {
            queries.save(malId.toLong(), values.status.name, values.progress.toLong(), values.score?.toLong())
        }
    }

    override suspend fun delete(malId: Int) {
        withContext(dispatcher) { queries.deleteByMalId(malId.toLong()) }
    }

    override suspend fun clear() {
        withContext(dispatcher) { queries.clear() }
    }
}
