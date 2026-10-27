package com.greenzean.animekmp.shared.details.presentation

import kotlin.time.Instant

private const val EXTRA_EPISODES_WITHOUT_TOTAL = 12
private const val JST_OFFSET_SECONDS = 9 * 3600L
private const val SECONDS_PER_DAY = 86_400L
private const val DAYS_PER_WEEK = 7L
private const val DAYS_PER_400_YEARS = 146_097L
private const val DAYS_FROM_YEAR_0_TO_EPOCH = 719_468L

fun selectableEpisodes(
    details: AnimeDetailsUi?,
    storedTotal: Int?,
    progress: Int,
    now: Instant,
): Int {
    val total = (details?.episodes ?: storedTotal)?.takeIf { it > 0 }
    val start = details?.startDate
    val startEpochDay =
        if (details?.airingStatus == AiringStatus.Airing && start?.month != null && start.day != null) {
            epochDay(start.year, start.month, start.day)
        } else {
            null
        }
    val count =
        if (startEpochDay != null) {
            val today = (now.epochSeconds + JST_OFFSET_SECONDS).floorDiv(SECONDS_PER_DAY)
            val weeks = (today - startEpochDay).floorDiv(DAYS_PER_WEEK)
            val aired = (1L + weeks).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            if (total != null) minOf(aired, total) else aired
        } else {
            total ?: (progress + EXTRA_EPISODES_WITHOUT_TOTAL)
        }
    return maxOf(count, progress)
}

private fun epochDay(
    year: Int,
    month: Int,
    day: Int,
): Long {
    val y = if (month <= 2) year - 1L else year.toLong()
    val era = y.floorDiv(400L)
    val yearOfEra = y - era * 400
    val shiftedMonth = (month + 9) % 12
    val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * DAYS_PER_400_YEARS + dayOfEra - DAYS_FROM_YEAR_0_TO_EPOCH
}
