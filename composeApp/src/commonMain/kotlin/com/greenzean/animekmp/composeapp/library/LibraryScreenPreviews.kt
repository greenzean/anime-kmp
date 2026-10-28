package com.greenzean.animekmp.composeapp.library

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.core.label
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.components.AppSortMenu
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_library_sort
import com.greenzean.animekmp.composeapp.generated.resources.library_sort_label
import com.greenzean.animekmp.shared.library.domain.LibrarySort
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.EntryUi
import com.greenzean.animekmp.shared.library.presentation.LibraryState
import org.jetbrains.compose.resources.stringResource

@Preview
@Composable
private fun LibraryScreenLightPreview() = LibraryScreenPreview(darkTheme = false)

@Preview
@Composable
private fun LibraryScreenDarkPreview() = LibraryScreenPreview(darkTheme = true)

@Preview
@Composable
private fun LibraryEmptyLightPreview() = LibraryScreenPreview(darkTheme = false, state = LibraryState())

@Preview
@Composable
private fun LibraryEmptyDarkPreview() = LibraryScreenPreview(darkTheme = true, state = LibraryState())

@Preview
@Composable
private fun LibraryNoResultsLightPreview() = LibraryScreenPreview(darkTheme = false, state = previewNoResults)

@Preview
@Composable
private fun LibraryNoResultsDarkPreview() = LibraryScreenPreview(darkTheme = true, state = previewNoResults)

@Preview
@Composable
private fun LibrarySortMenuLightPreview() = LibrarySortMenuPreview(darkTheme = false)

@Preview
@Composable
private fun LibrarySortMenuDarkPreview() = LibrarySortMenuPreview(darkTheme = true)

@Composable
private fun LibraryScreenPreview(
    darkTheme: Boolean,
    state: LibraryState = previewFiltered,
) {
    ThemedPreview(darkTheme) { LibraryScreen(state = state, onAction = {}) }
}

@Composable
private fun LibrarySortMenuPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        AppSortMenu(
            label = stringResource(Res.string.library_sort_label),
            description = stringResource(Res.string.cd_library_sort),
            options = LibrarySort.entries,
            selected = LibrarySort.TITLE,
            optionLabel = { stringResource(it.sortLabel()) },
            onSelect = {},
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            initiallyExpanded = true,
        )
    }
}

private val previewNoResults =
    LibraryState(query = "Naruto", statusFilter = WatchStatus.DROPPED, isLibraryEmpty = false)

private val previewFiltered =
    LibraryState(
        statusFilter = WatchStatus.WATCHING,
        isLibraryEmpty = false,
        entries =
            listOf(
                previewEntry(
                    "1",
                    malId = 52991,
                    "Frieren: Beyond Journey's End",
                    WatchStatus.WATCHING,
                    progress = 12,
                    total = 28,
                    score = 9,
                ),
                previewEntry("2", malId = 9253, "Steins;Gate", WatchStatus.COMPLETED, progress = 24, total = 24),
                previewEntry("3", 1, "One Piece", WatchStatus.PLAN_TO_WATCH),
            ),
    )

private fun previewEntry(
    id: String,
    malId: Int,
    title: String,
    status: WatchStatus,
    progress: Int = 0,
    total: Int? = null,
    score: Int? = null,
) = EntryUi(
    id = id,
    malId = malId,
    title = title,
    imageUrl = null,
    status = status,
    progress = progress,
    totalEpisodes = total,
    score = score,
    syncState = SyncState.SYNCED,
)
