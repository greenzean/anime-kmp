package com.greenzean.animekmp.shared.auth.domain

import com.greenzean.animekmp.shared.sync.domain.AccountLibraryDataSource
import com.greenzean.animekmp.shared.sync.domain.SyncEngine

enum class LibraryChoice { KEEP, DELETE }

class SignOutUseCase(
    private val store: SessionStore,
    private val engine: SyncEngine,
    private val library: AccountLibraryDataSource,
    private val authApi: AuthApi,
) {
    suspend fun prepare(): Int {
        engine.sync()
        return library.pendingCount()
    }

    suspend operator fun invoke(choice: LibraryChoice) {
        val refreshToken = store.get()?.refreshToken
        engine.exclusive {
            when (choice) {
                LibraryChoice.KEEP -> library.detachFromAccount()
                LibraryChoice.DELETE -> library.clearLibrary()
            }
            store.clear()
        }
        if (refreshToken != null) authApi.signOut(refreshToken)
    }
}
