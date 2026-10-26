package com.greenzean.animekmp.shared.auth.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.sync.domain.AccountLibraryDataSource
import com.greenzean.animekmp.shared.sync.domain.SyncEngine

class DeleteAccountUseCase(
    private val provider: GoogleIdTokenProvider,
    private val api: AuthApi,
    private val store: SessionStore,
    private val engine: SyncEngine,
    private val library: AccountLibraryDataSource,
) {
    suspend operator fun invoke(): Result<Unit, SignInError> =
        when (val nonce = api.deleteAccountNonce()) {
            is Result.Error -> Result.Error(SignInError.Server(nonce.error))
            is Result.Success -> deleteWith(nonce.data)
        }

    private suspend fun deleteWith(nonce: String): Result<Unit, SignInError> =
        when (val idToken = provider.getIdToken(nonce)) {
            is Result.Error -> Result.Error(SignInError.Google(idToken.error))
            is Result.Success -> delete(idToken.data)
        }

    private suspend fun delete(idToken: String): Result<Unit, SignInError> =
        when (val result = api.deleteAccount(idToken)) {
            is Result.Error -> {
                Result.Error(SignInError.Server(result.error))
            }

            is Result.Success -> {
                engine.exclusive {
                    library.detachFromAccount()
                    store.clear()
                }
                Result.Success(Unit)
            }
        }
}
