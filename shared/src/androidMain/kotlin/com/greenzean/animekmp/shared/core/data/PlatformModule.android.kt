package com.greenzean.animekmp.shared.core.data

import app.cash.sqldelight.db.SqlDriver
import com.greenzean.animekmp.shared.auth.data.KeystoreSessionStore
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import com.greenzean.animekmp.shared.library.data.SharedPreferencesLibrarySettings
import com.greenzean.animekmp.shared.library.domain.LibrarySettings
import com.greenzean.animekmp.shared.mal.data.KeystoreMalTokenStore
import com.greenzean.animekmp.shared.mal.domain.MalTokenStore
import com.greenzean.animekmp.shared.mal.domain.RandomBytes
import com.greenzean.animekmp.shared.settings.data.SharedPreferencesDisplaySettings
import com.greenzean.animekmp.shared.settings.domain.DisplaySettings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.security.SecureRandom

actual val platformModule =
    module {
        single<SqlDriver> { DatabaseDriverFactory(androidContext()).create() }
        single<DisplaySettings> { SharedPreferencesDisplaySettings(androidContext()) }
        single<LibrarySettings> { SharedPreferencesLibrarySettings(androidContext()) }
        single<SessionStore> { KeystoreSessionStore(androidContext()) }
        single<MalTokenStore> { KeystoreMalTokenStore(androidContext()) }
        single<RandomBytes> {
            val random = SecureRandom()
            RandomBytes { size -> ByteArray(size).also(random::nextBytes) }
        }
    }
