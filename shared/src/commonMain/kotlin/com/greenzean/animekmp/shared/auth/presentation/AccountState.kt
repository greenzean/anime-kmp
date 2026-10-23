package com.greenzean.animekmp.shared.auth.presentation

import com.greenzean.animekmp.shared.auth.domain.MergeSummary
import com.greenzean.animekmp.shared.auth.domain.SignInError
import com.greenzean.animekmp.shared.core.domain.DataError

data class AccountState(
    val isSignedIn: Boolean = false,
    val isSigningIn: Boolean = false,
    val error: SignInError? = null,
    val merge: MergeState = MergeState.Idle,
    val signOutPrompt: SignOutPrompt? = null,
)

sealed interface MergeState {
    data object Idle : MergeState

    data object Merging : MergeState

    data class Done(
        val summary: MergeSummary,
    ) : MergeState

    data class Failed(
        val error: DataError,
    ) : MergeState
}

sealed interface SignOutPrompt {
    data object Preparing : SignOutPrompt

    data class Ready(
        val unsyncedCount: Int,
    ) : SignOutPrompt

    data object SigningOut : SignOutPrompt
}

sealed interface AccountAction {
    data object OnSignInWithGoogleClick : AccountAction

    data object OnDismissError : AccountAction

    data object OnRetryMerge : AccountAction

    data object OnDismissMerge : AccountAction

    data object OnSignOutClick : AccountAction

    data object OnKeepLibraryClick : AccountAction

    data object OnDeleteLibraryClick : AccountAction

    data object OnDismissSignOut : AccountAction
}
