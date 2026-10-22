package com.greenzean.animekmp.shared.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import com.greenzean.animekmp.shared.auth.domain.SignInError
import com.greenzean.animekmp.shared.auth.domain.SignInWithGoogleUseCase
import com.greenzean.animekmp.shared.core.domain.Result
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
) : ViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        sessionStore.session
            .onEach { session -> _state.update { it.copy(isSignedIn = session != null) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: AccountAction) {
        when (action) {
            AccountAction.OnSignInWithGoogleClick -> signIn()
            AccountAction.OnDismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun signIn() {
        if (_state.value.isSigningIn) return
        _state.update { it.copy(isSigningIn = true, error = null) }
        viewModelScope.launch {
            val result = signInWithGoogle()
            val error = (result as? Result.Error)?.error?.takeUnless(::isCancelled)
            _state.update { it.copy(isSigningIn = false, error = error) }
        }
    }

    private fun isCancelled(error: SignInError) = error == SignInError.Google(GoogleSignInError.CANCELLED)
}
