package com.greenzean.animekmp.shared.core.data

import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase

/**
 * Four anime for debug builds, so the app has something to add until MAL search arrives (M2-11).
 * Keyed by MAL id like every other anime row (ADR 0009), so running it again is a no-op.
 */
object DevAnimeSeed {
    private class SeedAnime(
        val malId: Long,
        val title: String,
    )

    private val anime =
        listOf(
            SeedAnime(1, "Cowboy Bebop"),
            SeedAnime(5114, "Fullmetal Alchemist: Brotherhood"),
            SeedAnime(9253, "Steins;Gate"),
            SeedAnime(16498, "Shingeki no Kyojin"),
        )

    fun apply(db: AnimeDatabase) {
        db.transaction {
            anime.forEach { db.animeQueries.insertOrIgnore(it.malId, it.title) }
        }
    }
}
