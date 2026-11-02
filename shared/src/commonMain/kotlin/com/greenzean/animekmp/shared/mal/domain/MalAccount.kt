package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.core.logging.AppLogger
import com.greenzean.animekmp.shared.core.logging.NoOpAppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

private const val TAG = "MalAuth"

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
    private val startImport: StartMalImportUseCase,
    private val logger: AppLogger = NoOpAppLogger,
) {
    suspend operator fun invoke(): Result<Unit, MalAuthError> =
        when (val login = auth.login()) {
            is Result.Error -> {
                logger.info(TAG, "connect failed: ${login.error.name}")
                login
            }

            is Result.Success -> {
                logger.info(TAG, "connect ok")
                val name = (userApi.me() as? Result.Success)?.data?.name
                store.setUsername(name)
                startImport()
                login
            }
        }
}

class DisconnectMalUseCase(
    private val auth: MalAuthManager,
    private val store: MalAccountStore,
    private val repository: MalAnimeRepository,
    private val importProgress: MalImportProgressStore,
    private val logger: AppLogger = NoOpAppLogger,
) {
    suspend operator fun invoke() {
        auth.logout()
        store.setUsername(null)
        store.setConsent(false)
        repository.clearCache()
        importProgress.clear()
        logger.info(TAG, "disconnected, cache cleared")
    }
}
