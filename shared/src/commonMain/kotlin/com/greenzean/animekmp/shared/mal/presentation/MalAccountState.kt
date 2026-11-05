package com.greenzean.animekmp.shared.mal.presentation

import com.greenzean.animekmp.shared.mal.domain.MalSendError
import kotlin.time.Instant

enum class MalAccountError {
    Cancelled,
    AccessDenied,
    NoBrowser,
    NoInternet,
    Other,
}

sealed interface MalAccountStatus {
    data object NeedsConsent : MalAccountStatus

    data object NotConnected : MalAccountStatus

    data object Connecting : MalAccountStatus

    data class Connected(
        val username: String?,
    ) : MalAccountStatus

    data class Error(
        val reason: MalAccountError,
    ) : MalAccountStatus
}

data class MalAccountState(
    val status: MalAccountStatus = MalAccountStatus.NotConnected,
    val pendingCount: Int = 0,
    val lastError: MalSendError? = null,
    val lastSyncedAt: Instant? = null,
    val syncing: Boolean = false,
    val linkedElsewhere: Boolean = false,
    val linkedToAccount: Boolean = false,
    val unlinkFailed: Boolean = false,
)

sealed interface MalAccountAction {
    data object OnConsentAccepted : MalAccountAction

    data object OnConnectClick : MalAccountAction

    data object OnDisconnectClick : MalAccountAction

    data object OnErrorDismissed : MalAccountAction

    data object OnRetryClick : MalAccountAction

    data object OnSyncNowClick : MalAccountAction

    data object OnNotOnThisDeviceClick : MalAccountAction

    data object OnUnlinkFromAccountClick : MalAccountAction

    data object OnUnlinkErrorDismissed : MalAccountAction
}
