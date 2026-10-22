package com.greenzean.animekmp.shared.auth.domain

import com.greenzean.animekmp.shared.core.domain.Error
import com.greenzean.animekmp.shared.core.domain.Result

enum class GoogleSignInError : Error {
    /** The user closed the account picker. Not shown as an error. */
    CANCELLED,
    NO_CREDENTIAL,

    /** The build has no Google server client ID. */
    NOT_CONFIGURED,
    UNKNOWN,
}

/** Platform seam: Credential Manager with Sign in with Google on Android. */
fun interface GoogleIdTokenProvider {
    /** Asks the user to pick a Google account. [nonce] ends up in the ID token's `nonce` claim (ADR 0010). */
    suspend fun getIdToken(nonce: String): Result<String, GoogleSignInError>
}
