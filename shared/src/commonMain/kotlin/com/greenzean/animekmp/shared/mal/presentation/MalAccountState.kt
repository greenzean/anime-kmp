package com.greenzean.animekmp.shared.mal.presentation

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
)

sealed interface MalAccountAction {
    data object OnConsentAccepted : MalAccountAction

    data object OnConnectClick : MalAccountAction

    data object OnDisconnectClick : MalAccountAction

    data object OnErrorDismissed : MalAccountAction
}
