package com.greenzean.animekmp.shared.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.auth.domain.DeleteAccountUseCase
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.auth.domain.LibraryChoice
import com.greenzean.animekmp.shared.auth.domain.MergeLibraryUseCase
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import com.greenzean.animekmp.shared.auth.domain.SignInError
import com.greenzean.animekmp.shared.auth.domain.SignInWithGoogleUseCase
import com.greenzean.animekmp.shared.auth.domain.SignOutUseCase
import com.greenzean.animekmp.shared.core.domain.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountViewModel(
    sessionStore: SessionStore,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val mergeLibrary: MergeLibraryUseCase,
    private val signOut: SignOutUseCase,
    private val deleteAccount: DeleteAccountUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()
    private var prepareJob: Job? = null

    init {
        sessionStore.session
            .onEach { session ->
                _state.update {
                    it.copy(
                        isSignedIn = session != null,
                        sessionChecked = true,
                        merge =
                            if (session ==
                                null
                            ) {
                                MergeState.Idle
                            } else {
                                it.merge
                            },
                    )
                }
            }.launchIn(viewModelScope)
    }

    fun onAction(action: AccountAction) {
        when (action) {
            AccountAction.OnSignInWithGoogleClick -> signIn()
            AccountAction.OnDismissError -> _state.update { it.copy(error = null) }
            AccountAction.OnRetryMerge -> if (_state.value.merge !is MergeState.Merging) merge()
            AccountAction.OnDismissMerge -> _state.update { it.copy(merge = MergeState.Idle) }
            AccountAction.OnSignOutClick -> prepareSignOut()
            AccountAction.OnKeepLibraryClick -> applySignOut(LibraryChoice.KEEP)
            AccountAction.OnDeleteLibraryClick -> applySignOut(LibraryChoice.DELETE)
            AccountAction.OnDismissSignOut -> dismissSignOut()
            AccountAction.OnDeleteAccountClick -> _state.update { it.copy(deletePrompt = DeletePrompt.Confirming) }
            AccountAction.OnConfirmDeleteAccountClick -> confirmDeleteAccount()
            AccountAction.OnDismissDeleteAccount -> dismissDeleteAccount()
        }
    }

    private fun signIn() {
        if (_state.value.isSigningIn) return
        _state.update { it.copy(isSigningIn = true, error = null) }
        viewModelScope.launch {
            val result = signInWithGoogle()
            val error = (result as? Result.Error)?.error?.takeUnless(::isCancelled)
            _state.update { it.copy(isSigningIn = false, error = error) }
            if (result is Result.Success) merge().join()
        }
    }

    private fun merge() =
        viewModelScope.launch {
            _state.update { it.copy(merge = MergeState.Merging) }
            val merge =
                when (val result = mergeLibrary()) {
                    is Result.Success -> MergeState.Done(result.data)
                    is Result.Error -> MergeState.Failed(result.error)
                }
            _state.update { it.copy(merge = merge) }
        }

    private fun prepareSignOut() {
        if (_state.value.signOutPrompt != null) return
        _state.update { it.copy(signOutPrompt = SignOutPrompt.Preparing) }
        prepareJob =
            viewModelScope.launch {
                val unsynced = signOut.prepare()
                _state.update { it.copy(signOutPrompt = SignOutPrompt.Ready(unsynced)) }
            }
    }

    private fun applySignOut(choice: LibraryChoice) {
        if (_state.value.signOutPrompt !is SignOutPrompt.Ready) return
        _state.update { it.copy(signOutPrompt = SignOutPrompt.SigningOut) }
        viewModelScope.launch {
            signOut(choice)
            _state.update { it.copy(signOutPrompt = null, merge = MergeState.Idle) }
        }
    }

    private fun dismissSignOut() {
        if (_state.value.signOutPrompt == SignOutPrompt.SigningOut) return
        prepareJob?.cancel()
        _state.update { it.copy(signOutPrompt = null) }
    }

    private fun confirmDeleteAccount() {
        val prompt = _state.value.deletePrompt
        if (prompt != DeletePrompt.Confirming && prompt !is DeletePrompt.Failed) return
        _state.update { it.copy(deletePrompt = DeletePrompt.Deleting) }
        viewModelScope.launch {
            val result = deleteAccount()
            _state.update {
                it.copy(
                    deletePrompt =
                        when {
                            result is Result.Success -> null
                            isCancelled((result as Result.Error).error) -> DeletePrompt.Confirming
                            else -> DeletePrompt.Failed(result.error)
                        },
                    merge = if (result is Result.Success) MergeState.Idle else it.merge,
                )
            }
        }
    }

    private fun dismissDeleteAccount() {
        if (_state.value.deletePrompt == DeletePrompt.Deleting) return
        _state.update { it.copy(deletePrompt = null) }
    }

    private fun isCancelled(error: SignInError) = error == SignInError.Google(GoogleSignInError.CANCELLED)
}
