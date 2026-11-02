package com.greenzean.animekmp.composeapp.conflicts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenzean.animekmp.composeapp.core.label
import com.greenzean.animekmp.composeapp.designsystem.AppScaffold
import com.greenzean.animekmp.composeapp.designsystem.components.AnimeCover
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.components.AppCard
import com.greenzean.animekmp.composeapp.designsystem.components.AppDialog
import com.greenzean.animekmp.composeapp.designsystem.components.EmptyState
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_cancel
import com.greenzean.animekmp.composeapp.generated.resources.cd_conflicts_keep_app
import com.greenzean.animekmp.composeapp.generated.resources.cd_conflicts_keep_mal
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_app_label
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_empty_message
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_empty_title
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_keep_all_app
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_keep_all_mal
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_keep_all_mal_dialog_confirm
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_keep_all_mal_dialog_message
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_keep_all_mal_dialog_title
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_keep_app
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_keep_mal
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_mal_label
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_progress
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_removed
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_score
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_title
import com.greenzean.animekmp.composeapp.generated.resources.entry_score_none
import com.greenzean.animekmp.shared.conflict.domain.EntryValues
import com.greenzean.animekmp.shared.conflict.presentation.ConflictUi
import com.greenzean.animekmp.shared.conflict.presentation.ConflictsAction
import com.greenzean.animekmp.shared.conflict.presentation.ConflictsState
import com.greenzean.animekmp.shared.conflict.presentation.ConflictsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val STACK_BREAKPOINT = 300.dp
private const val LARGE_FONT_SCALE = 1.3f

@Composable
fun ConflictsRoot(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConflictsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConflictsScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
fun ConflictsScreen(
    state: ConflictsState,
    onAction: (ConflictsAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        title = stringResource(Res.string.conflicts_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (state.showKeepAllMalDialog) {
                AppDialog(
                    title = stringResource(Res.string.conflicts_keep_all_mal_dialog_title),
                    text = stringResource(Res.string.conflicts_keep_all_mal_dialog_message),
                    confirmText = stringResource(Res.string.conflicts_keep_all_mal_dialog_confirm),
                    onConfirm = { onAction(ConflictsAction.OnKeepAllMalConfirm) },
                    dismissText = stringResource(Res.string.action_cancel),
                    onDismiss = { onAction(ConflictsAction.OnDismissDialog) },
                    confirmModifier = Modifier.testTag(ConflictsTags.KEEP_ALL_MAL_CONFIRM),
                )
            }
            when {
                state.items.isNotEmpty() -> {
                    KeepAllButtons(onAction = onAction)
                    ConflictList(items = state.items, onAction = onAction)
                }

                !state.isLoading -> {
                    EmptyState(
                        title = stringResource(Res.string.conflicts_empty_title),
                        message = stringResource(Res.string.conflicts_empty_message),
                        modifier = Modifier.padding(MaterialTheme.spacing.lg).testTag(ConflictsTags.EMPTY),
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepAllButtons(onAction: (ConflictsAction) -> Unit) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        AppButton(
            text = stringResource(Res.string.conflicts_keep_all_app),
            onClick = { onAction(ConflictsAction.OnKeepAllAppClick) },
            modifier = Modifier.weight(1f).testTag(ConflictsTags.KEEP_ALL_APP),
            style = AppButtonStyle.Outlined,
        )
        AppButton(
            text = stringResource(Res.string.conflicts_keep_all_mal),
            onClick = { onAction(ConflictsAction.OnKeepAllMalClick) },
            modifier = Modifier.weight(1f).testTag(ConflictsTags.KEEP_ALL_MAL),
            style = AppButtonStyle.Outlined,
        )
    }
}

@Composable
private fun ConflictList(
    items: List<ConflictUi>,
    onAction: (ConflictsAction) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    LazyColumn(
        modifier = Modifier.testTag(ConflictsTags.LIST),
        contentPadding =
            PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        items(items, key = { it.malId }) { item ->
            ConflictItem(item = item, onAction = onAction)
        }
    }
}

@Composable
private fun ConflictHeader(
    title: String,
    imageUrl: String?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimeCover(imageUrl)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

@Composable
private fun ConflictItem(
    item: ConflictUi,
    onAction: (ConflictsAction) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val keepAppDescription = stringResource(Res.string.cd_conflicts_keep_app, item.title)
    val keepMalDescription = stringResource(Res.string.cd_conflicts_keep_mal, item.title)
    AppCard(modifier = Modifier.testTag(ConflictsTags.item(item.malId))) {
        ConflictHeader(title = item.title, imageUrl = item.imageUrl)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stacked = maxWidth < STACK_BREAKPOINT || LocalDensity.current.fontScale > LARGE_FONT_SCALE
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Version(label = stringResource(Res.string.conflicts_app_label), values = item.app)
                    Version(label = stringResource(Res.string.conflicts_mal_label), values = item.mal)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Version(
                        label = stringResource(Res.string.conflicts_app_label),
                        values = item.app,
                        modifier = Modifier.weight(1f),
                    )
                    Version(
                        label = stringResource(Res.string.conflicts_mal_label),
                        values = item.mal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            AppButton(
                text = stringResource(Res.string.conflicts_keep_app),
                onClick = { onAction(ConflictsAction.OnKeepAppClick(item.malId)) },
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag(ConflictsTags.keepApp(item.malId))
                        .semantics { contentDescription = keepAppDescription },
                style = AppButtonStyle.Tonal,
            )
            AppButton(
                text = stringResource(Res.string.conflicts_keep_mal),
                onClick = { onAction(ConflictsAction.OnKeepMalClick(item.malId)) },
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag(ConflictsTags.keepMal(item.malId))
                        .semantics { contentDescription = keepMalDescription },
            )
        }
    }
}

@Composable
private fun Version(
    label: String,
    values: EntryValues,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (values.removed) {
            Text(stringResource(Res.string.conflicts_removed), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(values.status.label()), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(Res.string.conflicts_progress, values.progress),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                values.score?.let { stringResource(Res.string.conflicts_score, it) }
                    ?: stringResource(Res.string.entry_score_none),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
