package com.greenzean.animekmp.shared.conflict

import com.greenzean.animekmp.shared.conflict.domain.Conflict
import com.greenzean.animekmp.shared.conflict.domain.ConflictLocalDataSource
import com.greenzean.animekmp.shared.conflict.domain.EntryValues
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

class FakeConflictLocalDataSource : ConflictLocalDataSource {
    val saved = mutableListOf<Pair<Int, EntryValues>>()
    val deleted = mutableListOf<Int>()
    var gate: CompletableDeferred<Unit>? = null
    private val conflicts = MutableStateFlow<List<Conflict>>(emptyList())

    val current: List<Conflict> get() = conflicts.value

    override fun observeConflicts(): Flow<List<Conflict>> =
        flow {
            gate?.await()
            conflicts.collect { emit(it) }
        }

    override suspend fun getConflicts(): List<Conflict> = conflicts.value

    override suspend fun saveConflict(
        malId: Int,
        other: EntryValues,
    ) {
        saved += malId to other
        conflicts.update { list ->
            val existing = list.firstOrNull { it.malId == malId }
            val next = existing?.copy(other = other) ?: Conflict(malId, "", null, other, other)
            list.filterNot { it.malId == malId } + next
        }
    }

    override suspend fun deleteConflict(malId: Int) {
        deleted += malId
        conflicts.update { list -> list.filterNot { it.malId == malId } }
    }

    fun put(conflict: Conflict) {
        conflicts.update { list -> list.filterNot { it.malId == conflict.malId } + conflict }
    }
}

fun values(
    status: WatchStatus = WatchStatus.COMPLETED,
    progress: Int = 26,
    score: Int? = 9,
    removed: Boolean = false,
) = EntryValues(status, progress, score, removed)

fun conflict(
    malId: Int = 1,
    title: String = "Cowboy Bebop",
    episodes: Int? = 26,
    app: EntryValues = values(WatchStatus.WATCHING, 5, 7),
    other: EntryValues = values(),
    imageUrl: String? = null,
) = Conflict(malId, title, episodes, app, other, imageUrl)
