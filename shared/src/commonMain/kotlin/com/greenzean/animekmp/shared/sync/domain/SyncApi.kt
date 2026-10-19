package com.greenzean.animekmp.shared.sync.domain

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result

/** Server side of the sync protocol (ADR 0006). Domain types only, so tests can fake it. */
interface SyncApi {
    suspend fun push(changes: List<SyncChange>): Result<List<PushResult>, DataError.Network>

    suspend fun pull(
        cursor: String?,
        limit: Int,
    ): Result<ChangesPage, DataError.Network>
}
