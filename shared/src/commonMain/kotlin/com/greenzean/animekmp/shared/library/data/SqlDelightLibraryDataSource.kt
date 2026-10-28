package com.greenzean.animekmp.shared.library.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import com.greenzean.animekmp.shared.library.domain.LibrarySort
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SqlDelightLibraryDataSource(
    private val db: AnimeDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LibraryLocalDataSource {
    override fun observeLibrary(
        query: String,
        status: WatchStatus?,
        sort: LibrarySort,
        titleLanguage: TitleLanguage,
    ): Flow<List<LibraryEntry>> =
        db.listEntryQueries
            .selectActiveWithAnime(
                status = status?.name,
                query = query.trim().escapeLike(),
                sort = sort.name,
                useEnglishTitle = if (titleLanguage == TitleLanguage.ENGLISH) 1L else 0L,
            ).asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toLibraryEntry() } }

    override fun observeActiveCount(): Flow<Int> =
        db.listEntryQueries
            .countActive()
            .asFlow()
            .mapToOne(dispatcher)
            .map { it.toInt() }

    override suspend fun getEntry(malId: Int): LibraryEntry? =
        withContext(dispatcher) {
            val entry = db.listEntryQueries.selectByMalId(malId.toLong()).executeAsOneOrNull()
            val anime = db.animeQueries.selectByMalId(malId.toLong()).executeAsOneOrNull()
            if (entry == null || anime == null || entry.deleted_at != null) null else entry.toLibraryEntry(anime)
        }

    override suspend fun saveAnime(anime: Anime) {
        withContext(dispatcher) {
            val malId = anime.malId.toLong()
            val episodes = anime.episodes?.toLong()
            db.transaction {
                db.animeQueries.insertOrIgnore(malId, anime.title, anime.titleEn, anime.imageUrl, episodes)
                db.animeQueries.updateDetails(anime.title, anime.titleEn, anime.imageUrl, episodes, malId)
            }
        }
    }

    override suspend fun saveEntry(
        malId: Int,
        status: WatchStatus,
        progress: Int,
        score: Int?,
    ) = withContext(dispatcher) {
        val now = clock.now().toEpochMilliseconds()
        db.transaction {
            db.listEntryQueries.updatePending(status.name, progress.toLong(), score?.toLong(), now, malId.toLong())
            db.listEntryQueries.insertPending(
                Uuid.random().toString(),
                malId.toLong(),
                status.name,
                progress.toLong(),
                score?.toLong(),
                now,
            )
        }
    }

    override suspend fun remove(malId: Int) =
        withContext(dispatcher) {
            db.listEntryQueries.softDelete(
                now = clock.now().toEpochMilliseconds(),
                malId = malId.toLong(),
            )
            Unit
        }
}

private fun String.escapeLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
