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
        startDate = startDate?.let(::parseMalDate),
    )
}

private fun String?.toAiringStatus(): AiringStatus? =
    when (this) {
        "finished_airing" -> AiringStatus.Finished
        "currently_airing" -> AiringStatus.Airing
        "not_yet_aired" -> AiringStatus.NotYetAired
        else -> null
    }

internal fun parseMalDate(raw: String): MalDate? {
    val parts = raw.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 }
    val day = if (month != null) parts.getOrNull(2)?.toIntOrNull() else null
    return MalDate(year, month, day)
}
