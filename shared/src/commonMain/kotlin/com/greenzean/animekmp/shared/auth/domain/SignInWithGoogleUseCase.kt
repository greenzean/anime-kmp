package com.greenzean.animekmp.shared.auth.domain

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Error
import com.greenzean.animekmp.shared.core.domain.Result

sealed interface SignInError : Error {
    data class Google(
        val reason: GoogleSignInError,
    ) : SignInError

    data class Server(
        val reason: DataError.Network,
    ) : SignInError
}

class SignInWithGoogleUseCase(
    private val provider: GoogleIdTokenProvider,
    private val api: AuthApi,
    private val store: SessionStore,
) {
    suspend operator fun invoke(): Result<Unit, SignInError> =
        when (val nonce = api.signInNonce()) {
            is Result.Error -> Result.Error(SignInError.Server(nonce.error))
            is Result.Success -> signInWith(nonce.data)
        }

    private suspend fun signInWith(nonce: String): Result<Unit, SignInError> =
        when (val idToken = provider.getIdToken(nonce)) {
            is Result.Error -> Result.Error(SignInError.Google(idToken.error))
            is Result.Success -> exchange(idToken.data)
        }

    private suspend fun exchange(idToken: String): Result<Unit, SignInError> =
        when (val session = api.signInWithGoogle(idToken)) {
            is Result.Error -> {
                Result.Error(SignInError.Server(session.error))
            }

            is Result.Success -> {
                store.save(session.data)
                Result.Success(Unit)
            }
        }
}
