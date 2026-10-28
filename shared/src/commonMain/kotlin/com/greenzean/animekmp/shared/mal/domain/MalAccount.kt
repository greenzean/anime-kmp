package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class MalAccount(
    val connected: Boolean,
    val username: String?,
    val consentGiven: Boolean,
)

class ObserveMalAccountUseCase(
    private val auth: MalAuthManager,
    private val store: MalAccountStore,
) {
    operator fun invoke(): Flow<MalAccount> =
        combine(auth.isLoggedIn, store.username, store.consentGiven) { connected, username, consent ->
            MalAccount(connected = connected, username = username, consentGiven = consent)
        }
}

class AcceptMalConsentUseCase(
    private val store: MalAccountStore,
) {
    operator fun invoke() = store.setConsent(true)
}

class ConnectMalUseCase(
    private val auth: MalAuthManager,
    private val userApi: MalUserApi,
    private val store: MalAccountStore,
) {
    suspend operator fun invoke(): Result<Unit, MalAuthError> =
        when (val login = auth.login()) {
            is Result.Error -> {
                login
            }

            is Result.Success -> {
                val name = (userApi.me() as? Result.Success)?.data?.name
                store.setUsername(name)
                login
            }
        }
}

class DisconnectMalUseCase(
    private val auth: MalAuthManager,
    private val store: MalAccountStore,
    private val repository: MalAnimeRepository,
) {
    suspend operator fun invoke() {
        auth.logout()
        store.setUsername(null)
        store.setConsent(false)
        repository.clearCache()
    }
}
