package com.greenzean.animekmp.shared.auth

import com.greenzean.animekmp.shared.auth.domain.AuthApi
import com.greenzean.animekmp.shared.auth.domain.GoogleIdTokenProvider
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Instant

val SESSION = Session("access", Instant.parse("2026-10-22T12:15:00Z"), "refresh")

class FakeAuthApi : AuthApi {
    var nonceResult: Result<String, DataError.Network> = Result.Success("nonce-1")
    var signInResult: Result<Session, DataError.Network> = Result.Success(SESSION)
    var signOutResult: Result<Unit, DataError.Network> = Result.Success(Unit)
    var deleteNonceResult: Result<String, DataError.Network> = Result.Success("delete-nonce-1")
    var deleteAccountResult: Result<Unit, DataError.Network> = Result.Success(Unit)
    val idTokens = mutableListOf<String>()
    val signedOutTokens = mutableListOf<String>()
    val deletedWithIdTokens = mutableListOf<String>()

    override suspend fun signInNonce() = nonceResult

    override suspend fun deleteAccountNonce() = deleteNonceResult

    override suspend fun signInWithGoogle(idToken: String): Result<Session, DataError.Network> {
        idTokens += idToken
        return signInResult
    }

    override suspend fun signOut(refreshToken: String): Result<Unit, DataError.Network> {
        signedOutTokens += refreshToken
        return signOutResult
    }

    override suspend fun deleteAccount(idToken: String): Result<Unit, DataError.Network> {
        deletedWithIdTokens += idToken
        return deleteAccountResult
    }
}

class FakeGoogleIdTokenProvider : GoogleIdTokenProvider {
    var result: Result<String, GoogleSignInError> = Result.Success("id-token")
    var gate: CompletableDeferred<Unit>? = null
    val nonces = mutableListOf<String>()

    override suspend fun getIdToken(nonce: String): Result<String, GoogleSignInError> {
        nonces += nonce
        gate?.await()
        return result
    }
}

class FakeSyncScheduler : SyncScheduler {
    var requests = 0

    override fun requestSync() {
        requests++
    }
}
