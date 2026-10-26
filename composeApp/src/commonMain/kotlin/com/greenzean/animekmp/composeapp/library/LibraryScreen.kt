package com.greenzean.animekmp.composeapp.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenzean.animekmp.composeapp.designsystem.AppScaffold
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.components.AnimeCover
import com.greenzean.animekmp.composeapp.designsystem.components.AppListRow
import com.greenzean.animekmp.composeapp.designsystem.components.EmptyState
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.EntryUi
import com.greenzean.animekmp.shared.library.presentation.LibraryState
import com.greenzean.animekmp.shared.library.presentation.LibraryViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryRoot(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryScreen(state = state, onNavigateToSettings = onNavigateToSettings, modifier = modifier)
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        title = "My library",
        modifier = modifier,
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(AppIcons.Settings, contentDescription = "Settings")
            }
        },
    ) { padding ->
        if (state.entries.isEmpty()) {
            EmptyState(
                title = "Your library is empty",
                message = "Find an anime in the Search tab and add it here.",
                modifier = Modifier.padding(padding).padding(MaterialTheme.spacing.lg),
            )
        } else {
            LibraryList(state = state, contentPadding = padding)
        }
    }
}

@Composable
private fun LibraryList(
    state: LibraryState,
    contentPadding: PaddingValues,
) {
    LazyColumn(contentPadding = contentPadding) {
        items(state.entries, key = { it.id }) { entry ->
            AppListRow(
                title = entry.title,
                supportingText = "${entry.status.label()} · ${entry.syncState.label()}",
                leading = { AnimeCover(entry.imageUrl) },
            )
        }
    }
}

private fun WatchStatus.label(): String =
    when (this) {
        WatchStatus.WATCHING -> "Watching"
        WatchStatus.COMPLETED -> "Completed"
        WatchStatus.ON_HOLD -> "On hold"
        WatchStatus.DROPPED -> "Dropped"
        WatchStatus.PLAN_TO_WATCH -> "Plan to watch"
    }

private fun SyncState.label(): String =
    when (this) {
        SyncState.PENDING -> "Pending"
        SyncState.SYNCED -> "Synced"
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
    ThemedPreview(darkTheme) { LibraryScreen(state = state, onNavigateToSettings = {}) }
}

private val previewLibrary =
    LibraryState(
        entries =
            listOf(
                EntryUi("1", "Frieren: Beyond Journey's End", null, WatchStatus.PLAN_TO_WATCH, SyncState.PENDING),
                EntryUi("2", "Steins;Gate", null, WatchStatus.COMPLETED, SyncState.SYNCED),
            ),
    )
