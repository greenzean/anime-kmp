package com.greenzean.animekmp.shared.conflict.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.greenzean.animekmp.shared.conflict.domain.Conflict
import com.greenzean.animekmp.shared.conflict.domain.ConflictLocalDataSource
import com.greenzean.animekmp.shared.conflict.domain.EntryValues
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.data.db.SelectAllWithEntry
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class SqlDelightConflictDataSource(
    private val db: AnimeDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ConflictLocalDataSource {
    override fun observeConflicts(): Flow<List<Conflict>> =
        db.conflictQueries
            .selectAllWithEntry()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toConflict() }.filterNot { it.app.sameAs(it.other) } }

    override suspend fun getConflicts(): List<Conflict> =
        withContext(dispatcher) {
            db.conflictQueries
                .selectAllWithEntry()
                .executeAsList()
                .map { it.toConflict() }
                .filterNot { it.app.sameAs(it.other) }
        }

    override suspend fun saveConflict(
        malId: Int,
        other: EntryValues,
    ) {
        withContext(dispatcher) {
            db.conflictQueries.insertOrReplace(
                mal_id = malId.toLong(),
                status = other.status.name,
                progress = other.progress.toLong(),
                score = other.score?.toLong(),
                removed = if (other.removed) 1L else 0L,
                created_at = clock.now().toEpochMilliseconds(),
            )
        }
    }

    override suspend fun deleteConflict(malId: Int) {
        withContext(dispatcher) { db.conflictQueries.deleteByMalId(malId.toLong()) }
    }
}

private fun EntryValues.sameAs(other: EntryValues): Boolean =
    if (removed || other.removed) removed == other.removed else this == other

private fun SelectAllWithEntry.toConflict(): Conflict {
    val appRemoved = app_entry_id == null || app_deleted_at != null
    return Conflict(
        malId = mal_id.toInt(),
        title = title,
        episodes = episodes?.toInt(),
        app =
            EntryValues(
                status = app_status?.let { WatchStatus.valueOf(it) } ?: WatchStatus.PLAN_TO_WATCH,
                progress = app_progress?.toInt() ?: 0,
                score = app_score?.toInt(),
                removed = appRemoved,
            ),
        other =
            EntryValues(
                status = WatchStatus.valueOf(status),
                progress = progress.toInt(),
                score = score?.toInt(),
                removed = removed != 0L,
            ),
        imageUrl = image_url,
    )
}
