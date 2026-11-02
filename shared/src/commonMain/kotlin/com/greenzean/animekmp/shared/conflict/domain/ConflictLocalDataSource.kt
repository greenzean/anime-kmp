package com.greenzean.animekmp.shared.conflict.domain

import kotlinx.coroutines.flow.Flow

interface ConflictLocalDataSource {
    fun observeConflicts(): Flow<List<Conflict>>

    suspend fun getConflicts(): List<Conflict>

    suspend fun saveConflict(
        malId: Int,
        other: EntryValues,
    )

    suspend fun deleteConflict(malId: Int)
}
