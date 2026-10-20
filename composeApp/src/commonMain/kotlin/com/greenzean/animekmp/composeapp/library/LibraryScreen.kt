package com.greenzean.animekmp.composeapp.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.AnimeUi
import com.greenzean.animekmp.shared.library.presentation.EntryUi
import com.greenzean.animekmp.shared.library.presentation.LibraryAction
import com.greenzean.animekmp.shared.library.presentation.LibraryState
import com.greenzean.animekmp.shared.library.presentation.LibraryViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryRoot(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    onAction: (LibraryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("My library", style = MaterialTheme.typography.headlineSmall) }
        if (state.entries.isEmpty()) {
            item { Text("Nothing here yet. Add an anime below.") }
        }
        items(state.entries, key = { it.id }) { entry -> EntryRow(entry) }
        item { Text("Add anime", style = MaterialTheme.typography.headlineSmall) }
        if (state.addable.isEmpty()) {
            item { Text("Every anime is already in your library.") }
        }
        items(state.addable, key = { "anime-${it.id}" }) { anime ->
            AddableRow(anime = anime, onAdd = { onAction(LibraryAction.OnAddClick(anime.id)) })
        }
    }
}

@Composable
private fun EntryRow(
    entry: EntryUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.title, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text("${entry.status.label()} · ${entry.syncState.label()}", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AddableRow(
    anime: AnimeUi,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(anime.title, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Button(onClick = onAdd) { Text("Add") }
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
private fun LibraryScreenPreview() {
    AppTheme(darkTheme = false) { LibraryScreenPreviewContent() }
}

@Preview
@Composable
private fun LibraryScreenDarkPreview() {
    AppTheme(darkTheme = true) { LibraryScreenPreviewContent() }
}

@Composable
private fun LibraryScreenPreviewContent() {
    Surface {
        LibraryScreen(
            state =
                LibraryState(
                    entries = listOf(EntryUi("1", "Steins;Gate", WatchStatus.PLAN_TO_WATCH, SyncState.PENDING)),
                    addable = listOf(AnimeUi(1, "Cowboy Bebop")),
                ),
            onAction = {},
        )
    }
}
