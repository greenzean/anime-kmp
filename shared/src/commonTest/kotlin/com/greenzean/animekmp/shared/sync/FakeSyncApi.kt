package com.greenzean.animekmp.shared.sync

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.sync.domain.ChangesPage
import com.greenzean.animekmp.shared.sync.domain.PushOutcome
import com.greenzean.animekmp.shared.sync.domain.PushResult
import com.greenzean.animekmp.shared.sync.domain.SyncApi
import com.greenzean.animekmp.shared.sync.domain.SyncChange

class FakeSyncApi : SyncApi {
    val pushed = mutableListOf<List<SyncChange>>()
    val pullCursors = mutableListOf<String?>()
    val calls = mutableListOf<String>()

    val pushOutcomes = mutableMapOf<String, PushResult>()
    var pushError: DataError.Network? = null
    var pages = mutableListOf<Result<ChangesPage, DataError.Network>>()
    var onPush: () -> Unit = {}

    override suspend fun push(changes: List<SyncChange>): Result<List<PushResult>, DataError.Network> {
        calls += "push"
        onPush()
        pushed += changes
        pushError?.let { return Result.Error(it) }
        return Result.Success(changes.map { pushOutcomes[it.id] ?: PushResult(it.id, PushOutcome.APPLIED, it.id) })
    }

    override suspend fun pull(
        cursor: String?,
        limit: Int,
    ): Result<ChangesPage, DataError.Network> {
        calls += "pull"
        pullCursors += cursor
        val empty = Result.Success(ChangesPage(cursor ?: "0", false, emptyList()))
        return if (pages.isEmpty()) empty else pages.removeAt(0)
    }
}
