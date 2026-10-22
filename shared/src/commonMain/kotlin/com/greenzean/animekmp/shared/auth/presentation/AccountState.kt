package com.greenzean.animekmp.shared.auth.presentation

import com.greenzean.animekmp.shared.auth.domain.SignInError

data class AccountState(
    val isSignedIn: Boolean = false,
    val isSigningIn: Boolean = false,
    val error: SignInError? = null,
)

sealed interface AccountAction {
    data object OnSignInWithGoogleClick : AccountAction

    data object OnDismissError : AccountAction
}
