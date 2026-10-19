package com.greenzean.animekmp.shared.core.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase

actual class DatabaseDriverFactory(
    private val context: Context,
    private val seedDevData: Boolean,
) {
    actual fun create(): SqlDriver {
        val schema: SqlSchema<QueryResult.Value<Unit>> = if (seedDevData) SeededSchema else AnimeDatabase.Schema
        return AndroidSqliteDriver(
            schema,
            context,
            "anime.db",
            callback =
                object : AndroidSqliteDriver.Callback(schema) {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                },
        )
    }
}
