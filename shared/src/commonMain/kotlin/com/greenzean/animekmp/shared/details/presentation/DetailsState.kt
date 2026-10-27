package com.greenzean.animekmp.shared.details.presentation

import com.greenzean.animekmp.shared.library.domain.WatchStatus

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

data class EntryEditUi(
    val status: WatchStatus,
    val progress: Int,
    val totalEpisodes: Int?,
    val score: Int?,
    val selectableEpisodes: Int,
)

data class DetailsState(
    val details: AnimeDetailsUi? = null,
    val entry: EntryEditUi? = null,
    val status: DetailsStatus = DetailsStatus.Loading,
) {
    val inLibrary: Boolean get() = entry != null
}

sealed interface DetailsAction {
    data object OnAddClick : DetailsAction

    data object OnRetryClick : DetailsAction

    data object OnBackClick : DetailsAction

    data class OnStatusChange(
        val status: WatchStatus,
    ) : DetailsAction

    data class OnProgressChange(
        val progress: Int,
    ) : DetailsAction

    data class OnScoreChange(
        val score: Int?,
    ) : DetailsAction

    data object OnRemoveClick : DetailsAction

    data object OnUndoRemoveClick : DetailsAction
}

sealed interface DetailsEvent {
    data object NavigateBack : DetailsEvent

    data object ShowRemoved : DetailsEvent
}
