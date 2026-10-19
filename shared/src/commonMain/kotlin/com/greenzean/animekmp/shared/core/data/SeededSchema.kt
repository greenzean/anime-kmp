package com.greenzean.animekmp.shared.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase

/** Schema that also loads the dev anime, once, when the database file is first created. */
object SeededSchema : SqlSchema<QueryResult.Value<Unit>> by AnimeDatabase.Schema {
    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        AnimeDatabase.Schema.create(driver)
        DevAnimeSeed.apply(AnimeDatabase(driver))
        return QueryResult.Unit
    }
}
