package com.greenzean.animekmp.composeapp.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenzean.animekmp.composeapp.designsystem.AppScaffold
import com.greenzean.animekmp.composeapp.designsystem.components.AnimeCover
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.components.AppListRow
import com.greenzean.animekmp.composeapp.designsystem.components.AppTextField
import com.greenzean.animekmp.composeapp.designsystem.components.EmptyState
import com.greenzean.animekmp.composeapp.designsystem.components.ErrorState
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.shared.search.presentation.SearchAction
import com.greenzean.animekmp.shared.search.presentation.SearchResultUi
import com.greenzean.animekmp.shared.search.presentation.SearchState
import com.greenzean.animekmp.shared.search.presentation.SearchStatus
import com.greenzean.animekmp.shared.search.presentation.SearchViewModel
import org.koin.compose.viewmodel.koinViewModel

private const val LOAD_MORE_DISTANCE = 3

@Composable
fun SearchRoot(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun SearchScreen(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(title = "Search", modifier = modifier) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            AppTextField(
                value = state.query,
                onValueChange = { onAction(SearchAction.OnQueryChange(it)) },
                label = "Search MyAnimeList",
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
            )
            SearchContent(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun SearchContent(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
) {
    val messagePadding = Modifier.padding(MaterialTheme.spacing.lg)
    when (state.status) {
        SearchStatus.Idle -> {
            EmptyState(
                title = "Find an anime",
                message = "Type at least 3 letters of a title.",
                icon = AppIcons.Search,
                modifier = messagePadding,
            )
        }

        SearchStatus.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = "Searching" })
            }
        }

        SearchStatus.Results -> {
            ResultList(state = state, onAction = onAction)
        }

        SearchStatus.Empty -> {
            EmptyState(
                title = "No results",
                message = "Nothing matched \"${state.query.trim()}\". Try another title.",
                icon = AppIcons.Search,
                modifier = messagePadding,
            )
        }

        SearchStatus.Offline -> {
            EmptyState(
                title = "You're offline",
                message = "Searching needs a connection. Your library still works.",
                icon = AppIcons.Warning,
                actionText = "Try again",
                onAction = { onAction(SearchAction.OnRetryClick) },
                modifier = messagePadding,
            )
        }

        SearchStatus.Error -> {
            ErrorState(
                message = "MyAnimeList could not be reached. Try again.",
                onRetry = { onAction(SearchAction.OnRetryClick) },
                modifier = messagePadding,
            )
        }
    }
}

@Composable
private fun ResultList(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(listState, state.canLoadMore) {
        if (state.canLoadMore) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            }.collect { last ->
                if (last >= state.results.size - LOAD_MORE_DISTANCE) currentOnAction(SearchAction.OnLoadMore)
            }
        }
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(top = MaterialTheme.spacing.sm)) {
        items(state.results, key = { it.malId }) { result ->
            AppListRow(
                title = result.title,
                leading = { AnimeCover(result.imageUrl) },
                trailing = { AddAction(result = result, onAdd = { onAction(SearchAction.OnAddClick(result.malId)) }) },
            )
        }
    }
}

@Composable
private fun AddAction(
    result: SearchResultUi,
    onAdd: () -> Unit,
) {
    if (result.inLibrary) {
        Text(
            "In library",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        AppButton(
            text = "Add",
            onClick = onAdd,
            style = AppButtonStyle.Tonal,
            modifier = Modifier.semantics { contentDescription = "Add ${result.title} to library" },
        )
    }
}
