package com.greenzean.animekmp.shared.auth.data

import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemorySessionStore : SessionStore {
    private val current = MutableStateFlow<Session?>(null)

    override val session: Flow<Session?> = current

    override suspend fun get(): Session? = current.value

    override suspend fun save(session: Session) {
        current.value = session
    }

    override suspend fun clear() {
        current.value = null
    }
}
