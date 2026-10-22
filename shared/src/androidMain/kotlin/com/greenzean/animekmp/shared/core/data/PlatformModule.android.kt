package com.greenzean.animekmp.shared.core.data

import android.content.Context
import android.content.pm.ApplicationInfo
import app.cash.sqldelight.db.SqlDriver
import com.greenzean.animekmp.shared.auth.data.KeystoreSessionStore
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule =
    module {
        single<SqlDriver> {
            val context = androidContext()
            DatabaseDriverFactory(context, seedDevData = context.isDebuggable()).create()
        }
        single<SessionStore> { KeystoreSessionStore(androidContext()) }
    }

private fun Context.isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
