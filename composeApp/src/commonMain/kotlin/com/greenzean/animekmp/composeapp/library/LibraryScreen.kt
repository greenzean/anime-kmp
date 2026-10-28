package com.greenzean.animekmp.composeapp.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenzean.animekmp.composeapp.core.ObserveAsEvents
import com.greenzean.animekmp.composeapp.core.label
import com.greenzean.animekmp.composeapp.designsystem.AppScaffold
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.components.AnimeCover
import com.greenzean.animekmp.composeapp.designsystem.components.AppListRow
import com.greenzean.animekmp.composeapp.designsystem.components.AppScoreStar
import com.greenzean.animekmp.composeapp.designsystem.components.EmptyState
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_library_increment
import com.greenzean.animekmp.composeapp.generated.resources.cd_settings
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_value
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_value_unknown
import com.greenzean.animekmp.composeapp.generated.resources.library_empty_message
import com.greenzean.animekmp.composeapp.generated.resources.library_empty_title
import com.greenzean.animekmp.composeapp.generated.resources.library_row_supporting
import com.greenzean.animekmp.composeapp.generated.resources.library_title
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.EntryUi
import com.greenzean.animekmp.shared.library.presentation.LibraryAction
import com.greenzean.animekmp.shared.library.presentation.LibraryEvent
import com.greenzean.animekmp.shared.library.presentation.LibraryState
import com.greenzean.animekmp.shared.library.presentation.LibraryViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryRoot(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetails: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is LibraryEvent.NavigateToDetails -> onNavigateToDetails(event.malId)
            LibraryEvent.NavigateToSettings -> onNavigateToSettings()
        }
    }
    LibraryScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    onAction: (LibraryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        title = stringResource(Res.string.library_title),
        modifier = modifier,
        actions = {
            IconButton(onClick = { onAction(LibraryAction.OnSettingsClick) }) {
                Icon(AppIcons.Settings, contentDescription = stringResource(Res.string.cd_settings))
            }
        },
    ) { padding ->
        if (state.entries.isEmpty()) {
            EmptyState(
                title = stringResource(Res.string.library_empty_title),
                message = stringResource(Res.string.library_empty_message),
                modifier = Modifier.padding(padding).padding(MaterialTheme.spacing.lg),
            )
        } else {
            LibraryList(state = state, onAction = onAction, contentPadding = padding)
        }
    }
}

@Composable
private fun LibraryList(
    state: LibraryState,
    onAction: (LibraryAction) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(contentPadding = contentPadding) {
        items(state.entries, key = { it.id }) { entry ->
            val status = stringResource(entry.status.label())
            val supportingText =
                if (entry.status == WatchStatus.PLAN_TO_WATCH) {
                    status
                } else {
                    stringResource(Res.string.library_row_supporting, status, entry.progressText())
                }
            val incrementDescription = stringResource(Res.string.cd_library_increment, entry.title)
            val hasScore = entry.score != null
            val canIncrement = entry.status == WatchStatus.WATCHING
            AppListRow(
                title = entry.title,
                supportingText = supportingText,
                onClick = { onAction(LibraryAction.OnEntryClick(entry.malId)) },
                leading = { AnimeCover(entry.imageUrl) },
                trailing =
                    if (hasScore || canIncrement) {
                        {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                            ) {
                                if (hasScore) {
                                    AppScoreStar(entry.score)
                                }
                                if (canIncrement) {
                                    IconButton(onClick = { onAction(LibraryAction.OnIncrementClick(entry.malId)) }) {
                                        Icon(AppIcons.Add, contentDescription = incrementDescription)
                                    }
                                }
                            }
                        }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun EntryUi.progressText(): String {
    val total = totalEpisodes
    return if (total == null) {
        stringResource(Res.string.entry_progress_value_unknown, progress)
    } else {
        stringResource(Res.string.entry_progress_value, progress, total)
    }
}

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

@Composable
private fun LibraryScreenPreview(
    darkTheme: Boolean,
    state: LibraryState = previewLibrary,
) {
    ThemedPreview(darkTheme) { LibraryScreen(state = state, onAction = {}) }
}

private val previewLibrary =
    LibraryState(
        entries =
            listOf(
                EntryUi(
                    "1",
                    52991,
                    "Frieren: Beyond Journey's End",
                    null,
                    WatchStatus.WATCHING,
                    12,
                    28,
                    9,
                    SyncState.PENDING,
                ),
                EntryUi("2", 9253, "Steins;Gate", null, WatchStatus.COMPLETED, 24, 24, null, SyncState.SYNCED),
                EntryUi("3", 1, "One Piece", null, WatchStatus.PLAN_TO_WATCH, 0, null, null, SyncState.SYNCED),
            ),
    )
