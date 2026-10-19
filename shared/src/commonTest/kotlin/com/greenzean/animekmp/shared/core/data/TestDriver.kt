package com.greenzean.animekmp.shared.core.data

import app.cash.sqldelight.db.SqlDriver

/** Fresh in-memory driver with the schema created. */
expect fun createTestDriver(): SqlDriver
