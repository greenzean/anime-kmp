package com.greenzean.animekmp.shared.sync.domain

interface AccountLibraryDataSource {
    suspend fun pendingCount(): Int

    suspend fun activeCount(): Int

    suspend fun clearLibrary()

    suspend fun detachFromAccount()
}
