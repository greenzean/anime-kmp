package com.greenzean.animekmp.shared.auth.data

import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.auth.domain.SessionStore

class InMemorySessionStore : SessionStore {
    private var session: Session? = null

    override suspend fun get(): Session? = session

    override suspend fun save(session: Session) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }
}
