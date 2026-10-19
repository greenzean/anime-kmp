package com.greenzean.animekmp.shared.core.data

import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase

/**
 * Same rows and ids as the server dev seed (V3__seed_dev_anime.sql, identity ids 1..4 in insert order).
 * Keyed on id, so running it again is a no-op.
 */
object DevAnimeSeed {
    private class SeedAnime(
        val id: Long,
        val malId: Long,
        val title: String,
    )

    private val anime =
        listOf(
            SeedAnime(1, 1, "Cowboy Bebop"),
            SeedAnime(2, 5114, "Fullmetal Alchemist: Brotherhood"),
            SeedAnime(3, 9253, "Steins;Gate"),
            SeedAnime(4, 16498, "Shingeki no Kyojin"),
        )

    fun apply(db: AnimeDatabase) {
        db.transaction {
            anime.forEach { db.animeQueries.insertOrIgnore(it.id, it.malId, it.title) }
        }
    }
}
