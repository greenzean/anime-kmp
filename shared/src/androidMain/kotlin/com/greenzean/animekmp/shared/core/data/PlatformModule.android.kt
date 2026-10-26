package com.greenzean.animekmp.shared.core.data

import android.content.Context
import android.content.pm.ApplicationInfo
import app.cash.sqldelight.db.SqlDriver
import com.greenzean.animekmp.shared.auth.data.KeystoreSessionStore
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import com.greenzean.animekmp.shared.mal.data.KeystoreMalTokenStore
import com.greenzean.animekmp.shared.mal.domain.MalTokenStore
import com.greenzean.animekmp.shared.mal.domain.RandomBytes
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.security.SecureRandom

actual val platformModule =
    module {
        single<SqlDriver> {
            val context = androidContext()
            DatabaseDriverFactory(context, seedDevData = context.isDebuggable()).create()
        }
        single<SessionStore> { KeystoreSessionStore(androidContext()) }
        single<MalTokenStore> { KeystoreMalTokenStore(androidContext()) }
        single<RandomBytes> {
            val random = SecureRandom()
            RandomBytes { size -> ByteArray(size).also(random::nextBytes) }
        }
    }

private fun Context.isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
