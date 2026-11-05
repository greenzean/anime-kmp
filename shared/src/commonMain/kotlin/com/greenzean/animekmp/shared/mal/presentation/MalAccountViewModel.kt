package com.greenzean.animekmp.shared.mal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.AcceptMalConsentUseCase
import com.greenzean.animekmp.shared.mal.domain.ConnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DisconnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DismissMalPromptUseCase
import com.greenzean.animekmp.shared.mal.domain.MalAccount
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.ObserveMalAccountUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalLinkStateUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalOutboxUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalPullUseCase
import com.greenzean.animekmp.shared.mal.domain.RetryMalSendUseCase
import com.greenzean.animekmp.shared.mal.domain.SyncMalNowUseCase
import com.greenzean.animekmp.shared.mal.domain.UnlinkMalFromAccountUseCase
import kotlinx.coroutines.flow.Flow
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
    observeMalOutbox: ObserveMalOutboxUseCase,
    private val retryMalSend: RetryMalSendUseCase,
    observeMalPull: ObserveMalPullUseCase,
    private val syncMalNow: SyncMalNowUseCase,
    observeMalLinkState: ObserveMalLinkStateUseCase,
    private val dismissMalPrompt: DismissMalPromptUseCase,
    private val unlinkMalFromAccount: UnlinkMalFromAccountUseCase,
) : ViewModel() {
    private val override = MutableStateFlow<MalAccountStatus?>(null)
    private val unlinkFailed = MutableStateFlow(false)

    private val account: Flow<MalAccountState> =
        combine(observeMalAccount(), override, observeMalOutbox(), observeMalPull(), observeMalLinkState()) {
            account,
            local,
            outbox,
            pull,
            link,
            ->
            MalAccountState(
                status = local ?: account.toStatus(),
                pendingCount = outbox.pendingCount,
                lastError = outbox.lastError,
                lastSyncedAt = pull.lastSyncedAt,
                syncing = pull.syncing,
                linkedElsewhere = link.linkedElsewhere,
                linkedToAccount = link.linkedToAccount,
            )
        }

    val state: StateFlow<MalAccountState> =
        combine(account, unlinkFailed) { state, failed -> state.copy(unlinkFailed = failed) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MalAccountState())

    fun onAction(action: MalAccountAction) {
        when (action) {
            MalAccountAction.OnConsentAccepted -> acceptMalConsent()
            MalAccountAction.OnConnectClick -> connect()
            MalAccountAction.OnDisconnectClick -> viewModelScope.launch { disconnectMal() }
            MalAccountAction.OnRetryClick -> viewModelScope.launch { retryMalSend() }
            MalAccountAction.OnSyncNowClick -> syncMalNow()
            MalAccountAction.OnNotOnThisDeviceClick -> dismissMalPrompt()
            MalAccountAction.OnUnlinkFromAccountClick -> unlink()
            MalAccountAction.OnUnlinkErrorDismissed -> unlinkFailed.value = false
            MalAccountAction.OnErrorDismissed -> override.update { if (it is MalAccountStatus.Error) null else it }
        }
    }

    private fun unlink() {
        unlinkFailed.value = false
        viewModelScope.launch {
            unlinkFailed.value = unlinkMalFromAccount() is Result.Error
        }
    }

    private fun connect() {
        if (override.value == MalAccountStatus.Connecting) return
        if (state.value.status == MalAccountStatus.NeedsConsent) return
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
