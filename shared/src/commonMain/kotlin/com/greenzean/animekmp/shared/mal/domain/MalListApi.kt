package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.domain.MalExportEntry
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import kotlin.time.Instant

data class MalListItem(
    val entry: MalExportEntry,
    val imageUrl: String?,
    val updatedAt: Instant? = null,
)

data class MalListPage(
    val items: List<MalListItem>,
    val hasNext: Boolean,
)

interface MalListApi {
    suspend fun page(
        offset: Int,
        limit: Int,
    ): Result<MalListPage, MalApiError>

    suspend fun update(
        malId: Int,
        status: WatchStatus,
        progress: Int,
        score: Int?,
    ): Result<Unit, MalApiError>

    suspend fun delete(malId: Int): Result<Unit, MalApiError>
}

interface MalImportProgressStore {
    suspend fun offset(): Int

    suspend fun setOffset(offset: Int)

    suspend fun clear()
}
