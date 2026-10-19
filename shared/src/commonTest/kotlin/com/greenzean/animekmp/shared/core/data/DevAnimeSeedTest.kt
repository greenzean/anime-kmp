package com.greenzean.animekmp.shared.core.data

import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DevAnimeSeedTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver)

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun seedsFourDevAnime() {
        DevAnimeSeed.apply(db)

        assertEquals(4L, db.animeQueries.countAll().executeAsOne())
        val steinsGate = db.animeQueries.selectByMalId(9253).executeAsOne()
        assertEquals(3L, steinsGate.id)
        assertEquals("Steins;Gate", steinsGate.title)
    }

    @Test
    fun seedingTwiceKeepsIds() {
        DevAnimeSeed.apply(db)
        val before = db.animeQueries.selectAll().executeAsList()

        DevAnimeSeed.apply(db)

        assertEquals(before, db.animeQueries.selectAll().executeAsList())
    }
}
