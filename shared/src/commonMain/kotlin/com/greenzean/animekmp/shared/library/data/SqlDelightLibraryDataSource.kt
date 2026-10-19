package com.greenzean.animekmp.shared.library.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.LOCAL_USER_ID
import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Local-only for now; M1-5/M1-6 add the remote side and sync. */
@OptIn(ExperimentalUuidApi::class)
class SqlDelightLibraryDataSource(
    private val db: AnimeDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LibraryLocalDataSource {
    override fun observeAnime(): Flow<List<Anime>> =
        db.animeQueries
            .selectAll()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toAnime() } }

    override fun observeLibrary(): Flow<List<LibraryEntry>> =
        db.listEntryQueries
            .selectActiveWithAnime(LOCAL_USER_ID)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toLibraryEntry() } }

    override suspend fun setStatus(
        animeId: Long,
        status: WatchStatus,
    ) = withContext(dispatcher) {
        val now = clock.now().toEpochMilliseconds()
        db.transaction {
            db.listEntryQueries.updatePending(status.name, now, LOCAL_USER_ID, animeId)
            db.listEntryQueries.insertPending(Uuid.random().toString(), LOCAL_USER_ID, animeId, status.name, now)
        }
    }

    override suspend fun remove(animeId: Long) =
        withContext(dispatcher) {
            db.listEntryQueries.softDelete(
                now = clock.now().toEpochMilliseconds(),
                userId = LOCAL_USER_ID,
                animeId = animeId,
            )
            Unit
        }
}
