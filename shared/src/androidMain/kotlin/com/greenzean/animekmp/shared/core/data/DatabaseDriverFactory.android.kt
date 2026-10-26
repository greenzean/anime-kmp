package com.greenzean.animekmp.shared.core.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase

actual class DatabaseDriverFactory(
    private val context: Context,
) {
    actual fun create(): SqlDriver =
        AndroidSqliteDriver(
            AnimeDatabase.Schema,
            context,
            "anime.db",
            callback =
                object : AndroidSqliteDriver.Callback(AnimeDatabase.Schema) {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                },
        )
}
