package com.greenzean.animekmp.shared.library.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class EntryRulesTest {
    private fun entry(
        status: WatchStatus = WatchStatus.PLAN_TO_WATCH,
        progress: Int = 0,
        score: Int? = null,
        episodes: Int? = 12,
    ) = LibraryEntry(
        id = "entry-1",
        anime = Anime(1, "Cowboy Bebop", episodes = episodes),
        status = status,
        progress = progress,
        score = score,
        updatedAt = Instant.fromEpochMilliseconds(0),
        syncState = SyncState.SYNCED,
    )

    private fun apply(
        entry: LibraryEntry,
        edit: EntryEdit,
    ) = EntryRules.apply(entry, edit)

    @Test
    fun progressIsStored() {
        val result = apply(entry(WatchStatus.WATCHING, progress = 2), EntryEdit.Progress(5))

        assertEquals(5, result.progress)
        assertEquals(WatchStatus.WATCHING, result.status)
    }

    @Test
    fun negativeProgressIsClampedToZero() {
        assertEquals(0, apply(entry(WatchStatus.WATCHING, progress = 3), EntryEdit.Progress(-4)).progress)
    }

    @Test
    fun progressAboveTheTotalIsClampedToTheTotal() {
        assertEquals(12, apply(entry(WatchStatus.WATCHING, progress = 3), EntryEdit.Progress(40)).progress)
    }

    @Test
    fun progressWithUnknownTotalIsClampedToTheMaximum() {
        val result =
            apply(entry(WatchStatus.WATCHING, episodes = null), EntryEdit.Progress(EntryRules.MAX_PROGRESS + 1))

        assertEquals(EntryRules.MAX_PROGRESS, result.progress)
        assertEquals(WatchStatus.WATCHING, result.status)
    }

    @Test
    fun maximumProgressIsAllowedWithUnknownTotal() {
        assertEquals(10_000, EntryRules.MAX_PROGRESS)
        assertEquals(
            10_000,
            apply(entry(WatchStatus.WATCHING, episodes = null), EntryEdit.Progress(10_000)).progress,
        )
    }

    @Test
    fun progressAboveZeroWhilePlanToWatchStartsWatching() {
        val result = apply(entry(WatchStatus.PLAN_TO_WATCH), EntryEdit.Progress(1))

        assertEquals(WatchStatus.WATCHING, result.status)
        assertEquals(1, result.progress)
    }

    @Test
    fun progressZeroWhilePlanToWatchStaysPlanToWatch() {
        assertEquals(WatchStatus.PLAN_TO_WATCH, apply(entry(WatchStatus.PLAN_TO_WATCH), EntryEdit.Progress(0)).status)
    }

    @Test
    fun progressAboveZeroKeepsOnHoldAndDropped() {
        assertEquals(WatchStatus.ON_HOLD, apply(entry(WatchStatus.ON_HOLD), EntryEdit.Progress(3)).status)
        assertEquals(WatchStatus.DROPPED, apply(entry(WatchStatus.DROPPED), EntryEdit.Progress(3)).status)
    }

    @Test
    fun progressReachingTheTotalCompletes() {
        val result = apply(entry(WatchStatus.WATCHING, progress = 3), EntryEdit.Progress(12))

        assertEquals(WatchStatus.COMPLETED, result.status)
        assertEquals(12, result.progress)
    }

    @Test
    fun progressBeyondTheTotalCompletesAtTheTotal() {
        val result = apply(entry(WatchStatus.PLAN_TO_WATCH), EntryEdit.Progress(99))

        assertEquals(WatchStatus.COMPLETED, result.status)
        assertEquals(12, result.progress)
    }

    @Test
    fun progressReachingTheTotalCompletesFromAnyStatus() {
        assertEquals(WatchStatus.COMPLETED, apply(entry(WatchStatus.ON_HOLD), EntryEdit.Progress(12)).status)
        assertEquals(WatchStatus.COMPLETED, apply(entry(WatchStatus.DROPPED), EntryEdit.Progress(12)).status)
    }

    @Test
    fun progressWithUnknownTotalNeverCompletes() {
        val result = apply(entry(WatchStatus.WATCHING, episodes = null), EntryEdit.Progress(500))

        assertEquals(WatchStatus.WATCHING, result.status)
    }

    @Test
    fun loweringProgressOfACompletedEntryBelowTheTotalGivesWatching() {
        val result = apply(entry(WatchStatus.COMPLETED, progress = 12), EntryEdit.Progress(11))

        assertEquals(11, result.progress)
        assertEquals(WatchStatus.WATCHING, result.status)
    }

    @Test
    fun loweringProgressOfACompletedEntryToZeroGivesWatching() {
        val result = apply(entry(WatchStatus.COMPLETED, progress = 12), EntryEdit.Progress(0))

        assertEquals(0, result.progress)
        assertEquals(WatchStatus.WATCHING, result.status)
    }

    @Test
    fun keepingACompletedEntryAtTheTotalKeepsItCompleted() {
        val result = apply(entry(WatchStatus.COMPLETED, progress = 12), EntryEdit.Progress(12))

        assertEquals(12, result.progress)
        assertEquals(WatchStatus.COMPLETED, result.status)
    }

    @Test
    fun loweringProgressOfACompletedEntryWithUnknownTotalKeepsItCompleted() {
        val result = apply(entry(WatchStatus.COMPLETED, progress = 12, episodes = null), EntryEdit.Progress(11))

        assertEquals(11, result.progress)
        assertEquals(WatchStatus.COMPLETED, result.status)
    }

    @Test
    fun choosingCompletedWithKnownTotalSetsProgressToTheTotal() {
        val result = apply(entry(WatchStatus.WATCHING, progress = 4), EntryEdit.Status(WatchStatus.COMPLETED))

        assertEquals(WatchStatus.COMPLETED, result.status)
        assertEquals(12, result.progress)
    }

    @Test
    fun choosingCompletedWithUnknownTotalKeepsProgress() {
        val result =
            apply(entry(WatchStatus.WATCHING, progress = 4, episodes = null), EntryEdit.Status(WatchStatus.COMPLETED))

        assertEquals(WatchStatus.COMPLETED, result.status)
        assertEquals(4, result.progress)
    }

    @Test
    fun choosingAnotherStatusKeepsProgress() {
        val result = apply(entry(WatchStatus.WATCHING, progress = 4), EntryEdit.Status(WatchStatus.ON_HOLD))

        assertEquals(WatchStatus.ON_HOLD, result.status)
        assertEquals(4, result.progress)
    }

    @Test
    fun choosingPlanToWatchSetsProgressToZero() {
        val result = apply(entry(WatchStatus.WATCHING, progress = 4), EntryEdit.Status(WatchStatus.PLAN_TO_WATCH))

        assertEquals(WatchStatus.PLAN_TO_WATCH, result.status)
        assertEquals(0, result.progress)
    }

    @Test
    fun choosingPlanToWatchFromCompletedSetsProgressToZero() {
        val result = apply(entry(WatchStatus.COMPLETED, progress = 12), EntryEdit.Status(WatchStatus.PLAN_TO_WATCH))

        assertEquals(WatchStatus.PLAN_TO_WATCH, result.status)
        assertEquals(0, result.progress)
    }

    @Test
    fun scoreInRangeIsStored() {
        assertEquals(1, apply(entry(), EntryEdit.Score(1)).score)
        assertEquals(10, apply(entry(), EntryEdit.Score(10)).score)
        assertEquals(7, apply(entry(), EntryEdit.Score(7)).score)
    }

    @Test
    fun scoreNullClearsTheScore() {
        assertEquals(null, apply(entry(score = 8), EntryEdit.Score(null)).score)
    }

    @Test
    fun scoreZeroIsIgnored() {
        assertEquals(8, apply(entry(score = 8), EntryEdit.Score(0)).score)
    }

    @Test
    fun scoreElevenIsIgnored() {
        assertEquals(8, apply(entry(score = 8), EntryEdit.Score(11)).score)
    }

    @Test
    fun negativeScoreIsIgnored() {
        assertEquals(null, apply(entry(), EntryEdit.Score(-1)).score)
    }

    @Test
    fun scoreDoesNotChangeStatusOrProgress() {
        val result = apply(entry(WatchStatus.WATCHING, progress = 3), EntryEdit.Score(9))

        assertEquals(WatchStatus.WATCHING, result.status)
        assertEquals(3, result.progress)
    }

    @Test
    fun incrementAddsOne() {
        val result = apply(entry(WatchStatus.WATCHING, progress = 3), EntryEdit.Increment)

        assertEquals(4, result.progress)
        assertEquals(WatchStatus.WATCHING, result.status)
    }

    @Test
    fun incrementWhilePlanToWatchStartsWatching() {
        val result = apply(entry(WatchStatus.PLAN_TO_WATCH), EntryEdit.Increment)

        assertEquals(1, result.progress)
        assertEquals(WatchStatus.WATCHING, result.status)
    }

    @Test
    fun incrementOnACompletedEntryBelowTheTotalGivesWatching() {
        val result = apply(entry(WatchStatus.COMPLETED, progress = 5), EntryEdit.Increment)

        assertEquals(6, result.progress)
        assertEquals(WatchStatus.WATCHING, result.status)
    }

    @Test
    fun incrementOnTheLastEpisodeCompletes() {
        val result = apply(entry(WatchStatus.WATCHING, progress = 11), EntryEdit.Increment)

        assertEquals(12, result.progress)
        assertEquals(WatchStatus.COMPLETED, result.status)
    }

    @Test
    fun incrementAtTheTotalStaysAtTheTotal() {
        val result = apply(entry(WatchStatus.COMPLETED, progress = 12), EntryEdit.Increment)

        assertEquals(12, result.progress)
        assertEquals(WatchStatus.COMPLETED, result.status)
    }

    @Test
    fun incrementWithUnknownTotalStopsAtTheMaximum() {
        val result =
            apply(entry(WatchStatus.WATCHING, progress = EntryRules.MAX_PROGRESS, episodes = null), EntryEdit.Increment)

        assertEquals(EntryRules.MAX_PROGRESS, result.progress)
    }

    @Test
    fun editKeepsTheIdAnimeAndScore() {
        val original = entry(WatchStatus.WATCHING, progress = 1, score = 6)

        val result = apply(original, EntryEdit.Progress(2))

        assertEquals(original.id, result.id)
        assertEquals(original.anime, result.anime)
        assertEquals(6, result.score)
    }
}
