package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.importer.domain.MalExportEntry
import com.greenzean.animekmp.shared.library.domain.WatchStatus

data class MalBaseValues(
    val status: WatchStatus,
    val progress: Int,
    val score: Int?,
)

interface MalBaseStore {
    suspend fun get(malId: Int): MalBaseValues?

    suspend fun save(
        malId: Int,
        values: MalBaseValues,
    )

    suspend fun delete(malId: Int)

    suspend fun clear()
}

fun MalExportEntry.toBase(): MalBaseValues = MalBaseValues(status, progress, score)
