package com.greenzean.animekmp.shared.mal.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.mal.domain.MalPullStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant

class SqlDelightMalPullStore(
    private val db: AnimeDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MalPullStore {
    override fun observeLastPulledAt(): Flow<Instant?> =
        db.malPullQueries
            .selectLastPulledAt()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { it?.let(Instant::fromEpochMilliseconds) }

    override suspend fun lastPulledAt(): Instant? =
        withContext(dispatcher) {
            db.malPullQueries
                .selectLastPulledAt()
                .executeAsOneOrNull()
                ?.let(Instant::fromEpochMilliseconds)
        }

    override suspend fun setLastPulledAt(at: Instant) {
        withContext(dispatcher) { db.malPullQueries.saveLastPulledAt(at.toEpochMilliseconds()) }
    }

    override suspend fun clear() {
        withContext(dispatcher) { db.malPullQueries.clearLastPulledAt() }
    }
}
