package com.greenzean.animekmp

import android.app.Application
import com.greenzean.animekmp.shared.initKoin
import org.koin.android.ext.koin.androidContext

class AnimeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@AnimeApplication) }
    }
}
