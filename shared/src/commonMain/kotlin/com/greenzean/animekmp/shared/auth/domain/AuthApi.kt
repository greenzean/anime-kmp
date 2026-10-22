package com.greenzean.animekmp.shared.auth.domain

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result

interface AuthApi {
    suspend fun signInNonce(): Result<String, DataError.Network>

    suspend fun signInWithGoogle(idToken: String): Result<Session, DataError.Network>
}
