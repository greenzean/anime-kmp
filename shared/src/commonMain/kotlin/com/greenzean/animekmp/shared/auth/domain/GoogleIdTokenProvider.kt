package com.greenzean.animekmp.shared.auth.domain

import com.greenzean.animekmp.shared.core.domain.Error
import com.greenzean.animekmp.shared.core.domain.Result

enum class GoogleSignInError : Error {
    CANCELLED,
    NO_CREDENTIAL,
    NOT_CONFIGURED,
    UNKNOWN,
}

fun interface GoogleIdTokenProvider {
    suspend fun getIdToken(nonce: String): Result<String, GoogleSignInError>
}
