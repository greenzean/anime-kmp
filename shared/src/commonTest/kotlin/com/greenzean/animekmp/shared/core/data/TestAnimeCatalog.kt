package com.greenzean.animekmp.shared.core.data

import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase

object TestAnimeCatalog {
    private val anime =
        listOf(
            1L to "Cowboy Bebop",
            5114L to "Fullmetal Alchemist: Brotherhood",
            9253L to "Steins;Gate",
            16498L to "Shingeki no Kyojin",
        )

    fun apply(db: AnimeDatabase) {
        db.transaction {
            anime.forEach { (malId, title) -> db.animeQueries.insertOrIgnore(malId, title, null, null) }
        }
    }
}
