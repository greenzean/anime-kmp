package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.LinkedPlatformsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLinkedPlatformsRepository(
    linked: Boolean = false,
) : LinkedPlatformsRepository {
    private val state = MutableStateFlow(linked)
    var remoteLinked = linked
    var error: DataError.Network? = null
    var refreshCalls = 0
    var linkCalls = 0
    var linkIfCalls = 0
    var unlinkCalls = 0

    override fun observeMalLinked(): Flow<Boolean> = state

    override suspend fun refresh(): EmptyResult<DataError> {
        refreshCalls++
        error?.let { return Result.Error(it) }
        state.value = remoteLinked
        return Result.Success(Unit)
    }

    override suspend fun linkMal(): EmptyResult<DataError> {
        linkCalls++
        error?.let { return Result.Error(it) }
        remoteLinked = true
        state.value = true
        return Result.Success(Unit)
    }

    override suspend fun linkMalIf(condition: () -> Boolean): EmptyResult<DataError> {
        linkIfCalls++
        return if (condition()) linkMal() else Result.Success(Unit)
    }

    override suspend fun unlinkMal(): EmptyResult<DataError> {
        unlinkCalls++
        error?.let { return Result.Error(it) }
        remoteLinked = false
        state.value = false
        return Result.Success(Unit)
    }
}
