package com.greenzean.animekmp.composeapp.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenzean.animekmp.composeapp.core.ObserveAsEvents
import com.greenzean.animekmp.composeapp.core.label
import com.greenzean.animekmp.composeapp.designsystem.AppScaffold
import com.greenzean.animekmp.composeapp.designsystem.components.AnimeCover
import com.greenzean.animekmp.composeapp.designsystem.components.AppFilterChip
import com.greenzean.animekmp.composeapp.designsystem.components.AppListRow
import com.greenzean.animekmp.composeapp.designsystem.components.AppScoreStar
import com.greenzean.animekmp.composeapp.designsystem.components.AppSearchField
import com.greenzean.animekmp.composeapp.designsystem.components.AppSortMenu
import com.greenzean.animekmp.composeapp.designsystem.components.EmptyState
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_library_increment
import com.greenzean.animekmp.composeapp.generated.resources.cd_library_search_clear
import com.greenzean.animekmp.composeapp.generated.resources.cd_library_sort
import com.greenzean.animekmp.composeapp.generated.resources.cd_settings
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_value
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_value_unknown
import com.greenzean.animekmp.composeapp.generated.resources.library_clear_filters
import com.greenzean.animekmp.composeapp.generated.resources.library_empty_message
import com.greenzean.animekmp.composeapp.generated.resources.library_empty_title
import com.greenzean.animekmp.composeapp.generated.resources.library_filter_all
import com.greenzean.animekmp.composeapp.generated.resources.library_no_results_message
import com.greenzean.animekmp.composeapp.generated.resources.library_no_results_title
import com.greenzean.animekmp.composeapp.generated.resources.library_row_supporting
import com.greenzean.animekmp.composeapp.generated.resources.library_search_hint
import com.greenzean.animekmp.composeapp.generated.resources.library_sort_label
import com.greenzean.animekmp.composeapp.generated.resources.library_sort_last_updated
import com.greenzean.animekmp.composeapp.generated.resources.library_sort_score
import com.greenzean.animekmp.composeapp.generated.resources.library_sort_title
import com.greenzean.animekmp.composeapp.generated.resources.library_title
import com.greenzean.animekmp.shared.library.domain.LibrarySort
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.EntryUi
import com.greenzean.animekmp.shared.library.presentation.LibraryAction
import com.greenzean.animekmp.shared.library.presentation.LibraryEvent
import com.greenzean.animekmp.shared.library.presentation.LibraryState
import com.greenzean.animekmp.shared.library.presentation.LibraryViewModel
import org.jetbrains.compose.resources.StringResource
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
        Column(modifier = Modifier.padding(padding)) {
            if (!state.isLibraryEmpty) {
                LibraryControls(state = state, onAction = onAction)
            }
            when {
                state.entries.isNotEmpty() -> {
                    LibraryList(state = state, onAction = onAction, contentPadding = PaddingValues())
                }

                state.isLibraryEmpty -> {
                    EmptyState(
                        title = stringResource(Res.string.library_empty_title),
                        message = stringResource(Res.string.library_empty_message),
                        modifier = Modifier.padding(MaterialTheme.spacing.lg),
                    )
                }

                else -> {
                    EmptyState(
                        title = stringResource(Res.string.library_no_results_title),
                        message = stringResource(Res.string.library_no_results_message),
                        icon = AppIcons.Search,
                        actionText = stringResource(Res.string.library_clear_filters),
                        onAction = {
                            onAction(LibraryAction.OnQueryChange(""))
                            onAction(LibraryAction.OnStatusFilterClick(null))
                        },
                        modifier =
                            Modifier
                                .padding(MaterialTheme.spacing.lg)
                                .testTag("library_no_results"),
                        actionModifier = Modifier.testTag("library_clear_filters"),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryControls(
    state: LibraryState,
    onAction: (LibraryAction) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        AppSearchField(
            value = state.query,
            onValueChange = { onAction(LibraryAction.OnQueryChange(it)) },
            placeholder = stringResource(Res.string.library_search_hint),
            clearDescription = stringResource(Res.string.cd_library_search_clear),
            modifier = Modifier.padding(horizontal = spacing.lg).testTag("library_search"),
            clearModifier = Modifier.testTag("library_search_clear"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "all") {
                    AppFilterChip(
                        label = stringResource(Res.string.library_filter_all),
                        selected = state.statusFilter == null,
                        onClick = { onAction(LibraryAction.OnStatusFilterClick(null)) },
                        modifier = Modifier.testTag("library_chip_all"),
                    )
                }
                items(WatchStatus.entries, key = { it.name }) { status ->
                    AppFilterChip(
                        label = stringResource(status.label()),
                        selected = state.statusFilter == status,
                        onClick = { onAction(LibraryAction.OnStatusFilterClick(status)) },
                        modifier = Modifier.testTag("library_chip_${status.name}"),
                    )
                }
            }
            AppSortMenu(
                label = stringResource(Res.string.library_sort_label),
                description = stringResource(Res.string.cd_library_sort),
                options = LibrarySort.entries,
                selected = state.sort,
                optionLabel = { stringResource(it.sortLabel()) },
                onSelect = { onAction(LibraryAction.OnSortChange(it)) },
                modifier = Modifier.padding(end = spacing.sm),
                buttonModifier = Modifier.testTag("library_sort_button"),
                optionModifier = { Modifier.testTag("library_sort_option_${it.name}") },
            )
        }
    }
}

internal fun LibrarySort.sortLabel(): StringResource =
    when (this) {
        LibrarySort.LAST_UPDATED -> Res.string.library_sort_last_updated
        LibrarySort.TITLE -> Res.string.library_sort_title
        LibrarySort.SCORE -> Res.string.library_sort_score
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
