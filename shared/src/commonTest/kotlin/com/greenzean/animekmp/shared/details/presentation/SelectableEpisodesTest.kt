package com.greenzean.animekmp.shared.details.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SelectableEpisodesTest {
    private val start = MalDate(2024, 1, 4)

    private fun details(
        airing: AiringStatus? = AiringStatus.Airing,
        episodes: Int? = null,
        startDate: MalDate? = start,
    ) = AnimeDetailsUi(
        malId = 1,
        title = "Show",
        otherTitle = null,
        imageUrl = null,
        synopsis = null,
        episodes = episodes,
        meanScore = null,
        genres = emptyList(),
        airingStatus = airing,
        startDate = startDate,
    )

    private fun at(text: String) = Instant.parse(text)

    @Test
    fun finishedShowEndsAtTheTotal() {
        assertEquals(
            12,
            selectableEpisodes(details(AiringStatus.Finished, episodes = 12), null, 3, at("2024-06-01T00:00:00Z")),
        )
    }

    @Test
    fun finishedShowNeverEndsBelowTheProgress() {
        assertEquals(
            15,
            selectableEpisodes(details(AiringStatus.Finished, episodes = 12), null, 15, at("2024-06-01T00:00:00Z")),
        )
    }

    @Test
    fun airingShowOnItsStartDayHasOneEpisode() {
        assertEquals(1, selectableEpisodes(details(episodes = 12), null, 0, at("2024-01-04T03:00:00Z")))
    }

    @Test
    fun airingShowAddsOneEpisodePerWeek() {
        assertEquals(1, selectableEpisodes(details(episodes = 12), null, 0, at("2024-01-10T03:00:00Z")))
        assertEquals(2, selectableEpisodes(details(episodes = 12), null, 0, at("2024-01-11T03:00:00Z")))
        assertEquals(3, selectableEpisodes(details(episodes = 12), null, 0, at("2024-01-18T03:00:00Z")))
    }

    @Test
    fun airingShowCountsDaysInJapanTime() {
        assertEquals(1, selectableEpisodes(details(episodes = 12), null, 0, at("2024-01-10T14:59:00Z")))
        assertEquals(2, selectableEpisodes(details(episodes = 12), null, 0, at("2024-01-10T15:00:00Z")))
    }

    @Test
    fun airingShowIsCappedAtTheTotal() {
        assertEquals(12, selectableEpisodes(details(episodes = 12), null, 0, at("2025-01-01T00:00:00Z")))
    }

    @Test
    fun airingShowWithUnknownTotalIsNotCapped() {
        assertEquals(52, selectableEpisodes(details(episodes = null), null, 0, at("2025-01-01T00:00:00Z")))
    }

    @Test
    fun airingShowBeforeItsStartDateHasAtLeastOneEpisode() {
        assertEquals(1, selectableEpisodes(details(episodes = 12), null, 0, at("2023-12-01T00:00:00Z")))
    }

    @Test
    fun airingShowNeverEndsBelowTheProgress() {
        assertEquals(8, selectableEpisodes(details(episodes = 12), null, 8, at("2024-01-04T03:00:00Z")))
    }

    @Test
    fun airingShowWithoutAStartDateEndsAtTheTotal() {
        assertEquals(
            12,
            selectableEpisodes(details(episodes = 12, startDate = null), null, 2, at("2024-06-01T00:00:00Z")),
        )
    }

    @Test
    fun airingShowWithAPartialStartDateEndsAtTheTotal() {
        val now = at("2024-06-01T00:00:00Z")

        listOf(MalDate(2024, null, null), MalDate(2024, 1, null)).forEach { partial ->
            assertEquals(12, selectableEpisodes(details(episodes = 12, startDate = partial), null, 2, now))
        }
    }

    @Test
    fun airingShowWithoutAStartDateAndTotalAddsTwelveToTheProgress() {
        assertEquals(
            14,
            selectableEpisodes(details(episodes = null, startDate = null), null, 2, at("2024-06-01T00:00:00Z")),
        )
    }

    @Test
    fun showThatIsNotAiringWithUnknownTotalAddsTwelveToTheProgress() {
        val now = at("2024-06-01T00:00:00Z")

        assertEquals(17, selectableEpisodes(details(AiringStatus.Finished, episodes = null), null, 5, now))
        assertEquals(17, selectableEpisodes(details(AiringStatus.NotYetAired, episodes = null), null, 5, now))
        assertEquals(17, selectableEpisodes(details(null, episodes = null), null, 5, now))
    }

    @Test
    fun showThatIsNotAiringWithAKnownTotalEndsAtTheTotal() {
        assertEquals(
            24,
            selectableEpisodes(details(AiringStatus.NotYetAired, episodes = 24), null, 0, at("2024-06-01T00:00:00Z")),
        )
    }

    @Test
    fun detailsTotalWinsOverTheStoredTotal() {
        assertEquals(
            12,
            selectableEpisodes(details(AiringStatus.Finished, episodes = 12), 24, 0, at("2024-06-01T00:00:00Z")),
        )
    }

    @Test
    fun storedTotalIsUsedWhenTheDetailsHaveNone() {
        assertEquals(
            24,
            selectableEpisodes(details(AiringStatus.Finished, episodes = null), 24, 0, at("2024-06-01T00:00:00Z")),
        )
    }

    @Test
    fun storedTotalCapsAnAiringShowWhenTheDetailsHaveNone() {
        assertEquals(24, selectableEpisodes(details(episodes = null), 24, 0, at("2030-01-01T00:00:00Z")))
    }

    @Test
    fun offlineUsesTheStoredTotal() {
        assertEquals(24, selectableEpisodes(null, 24, 3, at("2024-06-01T00:00:00Z")))
    }

    @Test
    fun offlineWithoutAnyTotalAddsTwelveToTheProgress() {
        assertEquals(15, selectableEpisodes(null, null, 3, at("2024-06-01T00:00:00Z")))
    }

    @Test
    fun offlineNeverEndsBelowTheProgress() {
        assertEquals(30, selectableEpisodes(null, 24, 30, at("2024-06-01T00:00:00Z")))
    }
}
