package com.greenzean.animekmp.shared.core.data

import app.cash.sqldelight.db.SqlDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import org.koin.dsl.module

val coreDataModule =
    module {
        single { AnimeDatabase(get<SqlDriver>()) }
    }
