package com.greenzean.animekmp.shared.mal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.AcceptMalConsentUseCase
import com.greenzean.animekmp.shared.mal.domain.ConnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DisconnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.MalAccount
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.ObserveMalAccountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

class MalAccountViewModel(
    observeMalAccount: ObserveMalAccountUseCase,
    private val acceptMalConsent: AcceptMalConsentUseCase,
    private val connectMal: ConnectMalUseCase,
    private val disconnectMal: DisconnectMalUseCase,
) : ViewModel() {
    private val override = MutableStateFlow<MalAccountStatus?>(null)

    val state: StateFlow<MalAccountState> =
        combine(observeMalAccount(), override) { account, local ->
            MalAccountState(local ?: account.toStatus())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MalAccountState())

    fun onAction(action: MalAccountAction) {
        when (action) {
            MalAccountAction.OnConsentAccepted -> acceptMalConsent()
            MalAccountAction.OnConnectClick -> connect()
            MalAccountAction.OnDisconnectClick -> viewModelScope.launch { disconnectMal() }
            MalAccountAction.OnErrorDismissed -> override.update { if (it is MalAccountStatus.Error) null else it }
        }
    }

    private fun connect() {
        if (override.value == MalAccountStatus.Connecting) return
        override.value = MalAccountStatus.Connecting
        viewModelScope.launch {
            override.value =
                when (val result = connectMal()) {
                    is Result.Success -> null
                    is Result.Error -> MalAccountStatus.Error(result.error.toAccountError())
                }
        }
    }
}

private fun MalAccount.toStatus(): MalAccountStatus =
    when {
        connected -> MalAccountStatus.Connected(username)
        consentGiven -> MalAccountStatus.NotConnected
        else -> MalAccountStatus.NeedsConsent
    }

private fun MalAuthError.toAccountError(): MalAccountError =
    when (this) {
        MalAuthError.CANCELLED -> MalAccountError.Cancelled
        MalAuthError.ACCESS_DENIED -> MalAccountError.AccessDenied
        MalAuthError.NO_BROWSER -> MalAccountError.NoBrowser
        MalAuthError.NO_INTERNET -> MalAccountError.NoInternet
        else -> MalAccountError.Other
    }
