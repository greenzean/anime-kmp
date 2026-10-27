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
import com.greenzean.animekmp.composeapp.core.ObserveAsEvents
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
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_add
import com.greenzean.animekmp.composeapp.generated.resources.action_try_again
import com.greenzean.animekmp.composeapp.generated.resources.cd_add_to_library
import com.greenzean.animekmp.composeapp.generated.resources.cd_searching
import com.greenzean.animekmp.composeapp.generated.resources.error_mal_unreachable
import com.greenzean.animekmp.composeapp.generated.resources.label_in_library
import com.greenzean.animekmp.composeapp.generated.resources.offline_title
import com.greenzean.animekmp.composeapp.generated.resources.search_empty_message
import com.greenzean.animekmp.composeapp.generated.resources.search_empty_title
import com.greenzean.animekmp.composeapp.generated.resources.search_field_label
import com.greenzean.animekmp.composeapp.generated.resources.search_idle_message
import com.greenzean.animekmp.composeapp.generated.resources.search_idle_title
import com.greenzean.animekmp.composeapp.generated.resources.search_offline_message
import com.greenzean.animekmp.composeapp.generated.resources.search_title
import com.greenzean.animekmp.shared.search.presentation.SearchAction
import com.greenzean.animekmp.shared.search.presentation.SearchEvent
import com.greenzean.animekmp.shared.search.presentation.SearchResultUi
import com.greenzean.animekmp.shared.search.presentation.SearchState
import com.greenzean.animekmp.shared.search.presentation.SearchStatus
import com.greenzean.animekmp.shared.search.presentation.SearchViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val LOAD_MORE_DISTANCE = 3

@Composable
fun SearchRoot(
    onNavigateToDetails: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SearchEvent.NavigateToDetails -> onNavigateToDetails(event.malId)
        }
    }
    SearchScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun SearchScreen(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(title = stringResource(Res.string.search_title), modifier = modifier) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            AppTextField(
                value = state.query,
                onValueChange = { onAction(SearchAction.OnQueryChange(it)) },
                label = stringResource(Res.string.search_field_label),
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
                title = stringResource(Res.string.search_idle_title),
                message = stringResource(Res.string.search_idle_message),
                icon = AppIcons.Search,
                modifier = messagePadding,
            )
        }

        SearchStatus.Loading -> {
            val searching = stringResource(Res.string.cd_searching)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = searching })
            }
        }

        SearchStatus.Results -> {
            ResultList(state = state, onAction = onAction)
        }

        SearchStatus.Empty -> {
            EmptyState(
                title = stringResource(Res.string.search_empty_title),
                message = stringResource(Res.string.search_empty_message, state.query.trim()),
                icon = AppIcons.Search,
                modifier = messagePadding,
            )
        }

        SearchStatus.Offline -> {
            EmptyState(
                title = stringResource(Res.string.offline_title),
                message = stringResource(Res.string.search_offline_message),
                icon = AppIcons.Warning,
                actionText = stringResource(Res.string.action_try_again),
                onAction = { onAction(SearchAction.OnRetryClick) },
                modifier = messagePadding,
            )
        }

        SearchStatus.Error -> {
            ErrorState(
                message = stringResource(Res.string.error_mal_unreachable),
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
                onClick = { onAction(SearchAction.OnResultClick(result.malId)) },
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
            stringResource(Res.string.label_in_library),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        val addDescription = stringResource(Res.string.cd_add_to_library, result.title)
        AppButton(
            text = stringResource(Res.string.action_add),
            onClick = onAdd,
            style = AppButtonStyle.Tonal,
            modifier = Modifier.semantics { contentDescription = addDescription },
        )
    }
}
