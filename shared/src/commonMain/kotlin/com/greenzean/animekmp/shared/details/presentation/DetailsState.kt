package com.greenzean.animekmp.shared.details.presentation

enum class DetailsStatus { Loading, Loaded, Offline, NotFound, Throttled, Error }

enum class AiringStatus { Finished, Airing, NotYetAired }

data class MalDate(
    val year: Int,
    val month: Int?,
    val day: Int?,
)

data class AnimeDetailsUi(
    val malId: Int,
    val title: String,
    val otherTitle: String?,
    val imageUrl: String?,
    val synopsis: String?,
    val episodes: Int?,
    val meanScore: Double?,
    val genres: List<String>,
    val airingStatus: AiringStatus?,
    val startDate: MalDate?,
)

data class DetailsState(
    val details: AnimeDetailsUi? = null,
    val inLibrary: Boolean = false,
    val status: DetailsStatus = DetailsStatus.Loading,
)

sealed interface DetailsAction {
    data object OnAddClick : DetailsAction

    data object OnRetryClick : DetailsAction

    data object OnBackClick : DetailsAction
}

sealed interface DetailsEvent {
    data object NavigateBack : DetailsEvent
}
