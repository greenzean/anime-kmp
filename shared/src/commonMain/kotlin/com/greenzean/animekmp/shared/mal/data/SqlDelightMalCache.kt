package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.mal.domain.Cached
import com.greenzean.animekmp.shared.mal.domain.MalAnimeCache
import com.greenzean.animekmp.shared.mal.domain.MalAnimeDetails
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Instant

private val json = Json { ignoreUnknownKeys = true }

class SqlDelightMalCache(
    private val db: AnimeDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MalAnimeCache {
    override suspend fun details(malId: Int): Cached<MalAnimeDetails>? =
        withContext(dispatcher) {
            db.malCacheQueries
                .selectDetails(malId.toLong())
                .executeAsOneOrNull()
                ?.let { decode<MalAnimeDetails>(it.payload, it.fetched_at) }
        }

    override suspend fun saveDetails(
        malId: Int,
        details: MalAnimeDetails,
        fetchedAt: Instant,
    ) {
        withContext(dispatcher) {
            db.malCacheQueries.saveDetails(
                malId.toLong(),
                json.encodeToString(details),
                fetchedAt.toEpochMilliseconds(),
            )
        }
    }

    override suspend fun search(
        queryKey: String,
        pageStart: Int,
    ): Cached<MalSearchPage>? =
        withContext(dispatcher) {
            db.malCacheQueries
                .selectSearch(queryKey, pageStart.toLong())
                .executeAsOneOrNull()
                ?.let { decode<MalSearchPage>(it.payload, it.fetched_at) }
        }

    override suspend fun saveSearch(
        queryKey: String,
        pageStart: Int,
        page: MalSearchPage,
        fetchedAt: Instant,
    ) {
        withContext(dispatcher) {
            db.malCacheQueries.saveSearch(
                queryKey,
                pageStart.toLong(),
                json.encodeToString(page),
                fetchedAt.toEpochMilliseconds(),
            )
        }
    }

    override suspend fun deleteFetchedBefore(threshold: Instant) =
        withContext(dispatcher) {
            db.transaction {
                db.malCacheQueries.deleteDetailsFetchedBefore(threshold.toEpochMilliseconds())
                db.malCacheQueries.deleteSearchFetchedBefore(threshold.toEpochMilliseconds())
            }
        }

    override suspend fun clear() =
        withContext(dispatcher) {
            db.transaction {
                db.malCacheQueries.deleteAllDetails()
                db.malCacheQueries.deleteAllSearch()
            }
        }

    private inline fun <reified T> decode(
        payload: String,
        fetchedAtMillis: Long,
    ): Cached<T>? =
        try {
            Cached(json.decodeFromString<T>(payload), Instant.fromEpochMilliseconds(fetchedAtMillis))
        } catch (_: SerializationException) {
            null
        }
}
