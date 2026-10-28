package com.greenzean.animekmp.composeapp.foryou

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.greenzean.animekmp.composeapp.designsystem.AppScaffold
import com.greenzean.animekmp.composeapp.designsystem.components.EmptyState
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_try_again
import com.greenzean.animekmp.composeapp.generated.resources.for_you_all_added_message
import com.greenzean.animekmp.composeapp.generated.resources.for_you_all_added_title
import com.greenzean.animekmp.composeapp.generated.resources.for_you_empty_message
import com.greenzean.animekmp.composeapp.generated.resources.for_you_empty_title
import com.greenzean.animekmp.composeapp.generated.resources.for_you_error_message
import com.greenzean.animekmp.composeapp.generated.resources.for_you_error_title
import com.greenzean.animekmp.composeapp.generated.resources.for_you_list
import com.greenzean.animekmp.composeapp.generated.resources.for_you_loading
import com.greenzean.animekmp.composeapp.generated.resources.for_you_not_connected_message
import com.greenzean.animekmp.composeapp.generated.resources.for_you_not_connected_title
import com.greenzean.animekmp.composeapp.generated.resources.for_you_offline_message
import com.greenzean.animekmp.composeapp.generated.resources.for_you_offline_title
import com.greenzean.animekmp.composeapp.generated.resources.for_you_title
import com.greenzean.animekmp.composeapp.mal.MalConnectionContent
import com.greenzean.animekmp.composeapp.search.LoadMoreEffect
import com.greenzean.animekmp.composeapp.search.SearchResultRow
import com.greenzean.animekmp.shared.mal.presentation.MalAccountAction
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.suggestions.presentation.SuggestionsAction
import com.greenzean.animekmp.shared.suggestions.presentation.SuggestionsState
import com.greenzean.animekmp.shared.suggestions.presentation.SuggestionsStatus
import org.jetbrains.compose.resources.stringResource

@Composable
fun ForYouScreen(
    state: SuggestionsState,
    malState: MalAccountState,
    onAction: (SuggestionsAction) -> Unit,
    onMalAction: (MalAccountAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(title = stringResource(Res.string.for_you_title), modifier = modifier) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            ForYouContent(state = state, malState = malState, onAction = onAction, onMalAction = onMalAction)
        }
    }
}

@Composable
private fun ForYouContent(
    state: SuggestionsState,
    malState: MalAccountState,
    onAction: (SuggestionsAction) -> Unit,
    onMalAction: (MalAccountAction) -> Unit,
) {
    when (state.status) {
        SuggestionsStatus.NotConnected -> {
            NotConnectedContent(malState, onMalAction)
        }

        SuggestionsStatus.Loading -> {
            LoadingContent()
        }

        SuggestionsStatus.Loaded -> {
            LoadedContent(state, onAction)
        }

        SuggestionsStatus.Empty -> {
            MessageContent(
                title = stringResource(Res.string.for_you_empty_title),
                message = stringResource(Res.string.for_you_empty_message),
                icon = AppIcons.Star,
            )
        }

        SuggestionsStatus.Offline -> {
            MessageContent(
                title = stringResource(Res.string.for_you_offline_title),
                message = stringResource(Res.string.for_you_offline_message),
                icon = AppIcons.Warning,
                onRetry = { onAction(SuggestionsAction.OnRetryClick) },
            )
        }

        SuggestionsStatus.Error -> {
            MessageContent(
                title = stringResource(Res.string.for_you_error_title),
                message = stringResource(Res.string.for_you_error_message),
                icon = AppIcons.Warning,
                onRetry = { onAction(SuggestionsAction.OnRetryClick) },
            )
        }
    }
}

@Composable
private fun NotConnectedContent(
    malState: MalAccountState,
    onMalAction: (MalAccountAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EmptyState(
            title = stringResource(Res.string.for_you_not_connected_title),
            message = stringResource(Res.string.for_you_not_connected_message),
            icon = AppIcons.Star,
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
        )
        MalConnectionContent(
            state = malState,
            onAction = onMalAction,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
        )
    }
}

@Composable
private fun LoadingContent() {
    val loading = stringResource(Res.string.for_you_loading)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier =
                Modifier
                    .testTag(ForYouTags.LOADING)
                    .semantics {
                        contentDescription = loading
                        liveRegion = LiveRegionMode.Polite
                    },
        )
    }
}

@Composable
private fun LoadedContent(
    state: SuggestionsState,
    onAction: (SuggestionsAction) -> Unit,
) {
    if (state.items.isEmpty()) {
        MessageContent(
            title = stringResource(Res.string.for_you_all_added_title),
            message = stringResource(Res.string.for_you_all_added_message),
            icon = AppIcons.Star,
        )
    } else {
        SuggestionList(state = state, onAction = onAction)
    }
}

@Composable
private fun MessageContent(
    title: String,
    message: String,
    icon: ImageVector,
    onRetry: (() -> Unit)? = null,
) {
    EmptyState(
        title = title,
        message = message,
        icon = icon,
        actionText = onRetry?.let { stringResource(Res.string.action_try_again) },
        onAction = onRetry,
        actionModifier = Modifier.testTag(ForYouTags.RETRY),
        modifier = Modifier.padding(MaterialTheme.spacing.lg),
    )
}

@Composable
private fun SuggestionList(
    state: SuggestionsState,
    onAction: (SuggestionsAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val listDescription = stringResource(Res.string.for_you_list)
    LoadMoreEffect(listState, state.items.size, state.canLoadMore) { onAction(SuggestionsAction.OnLoadMore) }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = MaterialTheme.spacing.sm),
        modifier = Modifier.testTag(ForYouTags.LIST).semantics { contentDescription = listDescription },
    ) {
        items(state.items, key = { it.malId }) { item ->
            SearchResultRow(
                result = item,
                onClick = { onAction(SuggestionsAction.OnItemClick(item.malId)) },
                onAdd = { onAction(SuggestionsAction.OnAddClick(item.malId)) },
                modifier = Modifier.testTag(ForYouTags.item(item.malId)),
                addModifier = Modifier.testTag(ForYouTags.add(item.malId)),
            )
        }
    }
}
