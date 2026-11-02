package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.domain.MalExportEntry

data class MalListItem(
    val entry: MalExportEntry,
    val imageUrl: String?,
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
}

interface MalImportProgressStore {
    suspend fun offset(): Int

    suspend fun setOffset(offset: Int)

    suspend fun clear()
}
