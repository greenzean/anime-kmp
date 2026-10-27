package com.greenzean.animekmp.shared.details.presentation

import com.greenzean.animekmp.shared.mal.domain.MalAnimeDetails
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import com.greenzean.animekmp.shared.settings.domain.pick

fun MalAnimeDetails.toDetailsUi(language: TitleLanguage): AnimeDetailsUi {
    val title = language.pick(summary.title, summary.titleEn)
    val otherTitle = listOfNotNull(summary.title, summary.titleEn).firstOrNull { it != title }
    return AnimeDetailsUi(
        malId = summary.malId,
        title = title,
        otherTitle = otherTitle,
        imageUrl = summary.imageUrl,
        synopsis = synopsis?.takeIf { it.isNotBlank() },
        episodes = summary.numEpisodes?.takeIf { it > 0 },
        meanScore = summary.mean,
        genres = genres,
        airingStatus = summary.status.toAiringStatus(),
        startDate = startDate?.let(::formatMalDate),
    )
}

private fun String?.toAiringStatus(): AiringStatus? =
    when (this) {
        "finished_airing" -> AiringStatus.Finished
        "currently_airing" -> AiringStatus.Airing
        "not_yet_aired" -> AiringStatus.NotYetAired
        else -> null
    }

private val MonthNames =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

internal fun formatMalDate(raw: String): String? {
    val parts = raw.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val month = parts.getOrNull(1)?.toIntOrNull()?.let { MonthNames.getOrNull(it - 1) }
    val day = parts.getOrNull(2)?.toIntOrNull()
    return when {
        month == null -> "$year"
        day == null -> "$month $year"
        else -> "$month $day, $year"
    }
}
