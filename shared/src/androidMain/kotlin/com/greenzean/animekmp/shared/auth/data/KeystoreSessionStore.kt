package com.greenzean.animekmp.shared.auth.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.time.Instant

/**
 * Keeps the session encrypted with AES-GCM under a key that never leaves the Android Keystore. Only the tokens
 * are encrypted; the expiry is not secret. A session that cannot be decrypted (key lost or invalidated) is
 * dropped, which signs the user out instead of crashing.
 */
class KeystoreSessionStore(
    context: Context,
) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                    .putString(KEY_ACCESS, encrypt(session.accessToken))
                    .putString(KEY_REFRESH, encrypt(session.refreshToken))
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

    /** Reads the stored session once; later calls use the in-memory copy. Call with [mutex] held. */
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
                accessToken = decrypt(access),
                accessTokenExpiresAt = Instant.fromEpochMilliseconds(prefs.getLong(KEY_EXPIRES_AT, 0)),
                refreshToken = decrypt(refresh),
            )
        } catch (_: GeneralSecurityException) {
            prefs.edit().clear().commit()
            null
        } catch (_: IllegalArgumentException) {
            prefs.edit().clear().commit()
            null
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + sealed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "Stored value is too short" }
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            }
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "animekmp_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
        const val PREFS_NAME = "session"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "access_token_expires_at"
    }
}
