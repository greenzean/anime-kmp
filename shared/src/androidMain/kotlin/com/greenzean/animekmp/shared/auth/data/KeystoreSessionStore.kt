package com.greenzean.animekmp.shared.auth.data

import android.content.Context
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import com.greenzean.animekmp.shared.core.data.KeystoreCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import kotlin.time.Instant

class KeystoreSessionStore(
    context: Context,
) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEY_ALIAS)
    private val mutex = Mutex()
    private val current = MutableStateFlow<Session?>(null)
    private var loaded = false

    override val session: Flow<Session?> = current

    override suspend fun get(): Session? = mutex.withLock { load() }

    override suspend fun save(session: Session) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                prefs
                    .edit()
                    .putString(KEY_ACCESS, cipher.encrypt(session.accessToken))
                    .putString(KEY_REFRESH, cipher.encrypt(session.refreshToken))
                    .putLong(KEY_EXPIRES_AT, session.accessTokenExpiresAt.toEpochMilliseconds())
                    .commit()
            }
            loaded = true
            current.value = session
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            withContext(Dispatchers.IO) { prefs.edit().clear().commit() }
            loaded = true
            current.value = null
        }
    }

    private suspend fun load(): Session? {
        if (!loaded) {
            current.value = withContext(Dispatchers.IO) { read() }
            loaded = true
        }
        return current.value
    }

    private fun read(): Session? {
        val access = prefs.getString(KEY_ACCESS, null)
        val refresh = prefs.getString(KEY_REFRESH, null)
        if (access == null || refresh == null || !prefs.contains(KEY_EXPIRES_AT)) return null
        return try {
            Session(
                accessToken = cipher.decrypt(access),
                accessTokenExpiresAt = Instant.fromEpochMilliseconds(prefs.getLong(KEY_EXPIRES_AT, 0)),
                refreshToken = cipher.decrypt(refresh),
            )
        } catch (_: GeneralSecurityException) {
            prefs.edit().clear().commit()
            null
        } catch (_: IllegalArgumentException) {
            prefs.edit().clear().commit()
            null
        } catch (_: ProviderException) {
            prefs.edit().clear().commit()
            null
        } catch (_: IOException) {
            prefs.edit().clear().commit()
            null
        }
    }

    private companion object {
        const val KEY_ALIAS = "animekmp_session"
        const val PREFS_NAME = "session"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "access_token_expires_at"
    }
}
