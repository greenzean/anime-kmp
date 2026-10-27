package com.greenzean.animekmp.composeapp.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.greenzean.animekmp.composeapp.core.label
import com.greenzean.animekmp.composeapp.designsystem.MinTouchTarget
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.components.AppGridDialog
import com.greenzean.animekmp.composeapp.designsystem.components.AppScoreStar
import com.greenzean.animekmp.composeapp.designsystem.components.AppSelectDialog
import com.greenzean.animekmp.composeapp.designsystem.components.AppStarRatingDialog
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_remove_from_library
import com.greenzean.animekmp.composeapp.generated.resources.cd_progress_decrement
import com.greenzean.animekmp.composeapp.generated.resources.cd_progress_edit
import com.greenzean.animekmp.composeapp.generated.resources.cd_progress_increment
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_dialog_title
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_label
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_value
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_value_unknown
import com.greenzean.animekmp.composeapp.generated.resources.entry_score_label
import com.greenzean.animekmp.composeapp.generated.resources.entry_score_none
import com.greenzean.animekmp.composeapp.generated.resources.entry_status_label
import com.greenzean.animekmp.shared.details.presentation.DetailsAction
import com.greenzean.animekmp.shared.details.presentation.EntryEditUi
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import org.jetbrains.compose.resources.stringResource

private enum class EditorDialog { None, Status, Progress, Score }

private const val MAX_SCORE = 10

@Composable
internal fun EntryEditor(
    entry: EntryEditUi,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf(EditorDialog.None) }
    val statusText = stringResource(entry.status.label())
    val progressText = entry.progressText()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        EditorRow(
            label = stringResource(Res.string.entry_status_label),
            onClick = { dialog = EditorDialog.Status },
        ) {
            Text(statusText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (entry.status != WatchStatus.PLAN_TO_WATCH) {
            ProgressRow(
                progressText = progressText,
                canDecrement = entry.progress > 1,
                onDecrement = { onAction(DetailsAction.OnProgressChange(entry.progress - 1)) },
                onIncrement = { onAction(DetailsAction.OnProgressChange(entry.progress + 1)) },
                onEdit = { dialog = EditorDialog.Progress },
            )
        }
        EditorRow(
            label = stringResource(Res.string.entry_score_label),
            onClick = { dialog = EditorDialog.Score },
        ) {
            AppScoreStar(entry.score)
        }
        AppButton(
            text = stringResource(Res.string.action_remove_from_library),
            onClick = { onAction(DetailsAction.OnRemoveClick) },
            style = AppButtonStyle.Destructive,
        )
    }
    EditorDialogHost(
        dialog = dialog,
        entry = entry,
        onAction = onAction,
        onClose = { dialog = EditorDialog.None },
    )
}

@Composable
private fun EditorDialogHost(
    dialog: EditorDialog,
    entry: EntryEditUi,
    onAction: (DetailsAction) -> Unit,
    onClose: () -> Unit,
) {
    when (dialog) {
        EditorDialog.None -> {
            Unit
        }

        EditorDialog.Status -> {
            StatusDialog(
                current = entry.status,
                onSelect = {
                    onClose()
                    onAction(DetailsAction.OnStatusChange(it))
                },
                onDismiss = onClose,
            )
        }

        EditorDialog.Progress -> {
            AppGridDialog(
                title = stringResource(Res.string.entry_progress_dialog_title),
                items = (1..entry.selectableEpisodes).toList(),
                selected = entry.progress,
                onSelect = {
                    onClose()
                    onAction(DetailsAction.OnProgressChange(it))
                },
                onDismiss = onClose,
            )
        }

        EditorDialog.Score -> {
            AppStarRatingDialog(
                title = stringResource(Res.string.entry_score_label),
                selected = entry.score,
                max = MAX_SCORE,
                onSelect = {
                    onClose()
                    onAction(DetailsAction.OnScoreChange(it))
                },
                clearText = stringResource(Res.string.entry_score_none),
                onClear = {
                    onClose()
                    onAction(DetailsAction.OnScoreChange(null))
                },
                onDismiss = onClose,
            )
        }
    }
}

@Composable
private fun EditorRow(
    label: String,
    onClick: () -> Unit,
    value: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MinTouchTarget)
                .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(MaterialTheme.spacing.lg))
        value()
    }
}

@Composable
private fun ProgressRow(
    progressText: String,
    canDecrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onEdit: () -> Unit,
) {
    val editDescription = stringResource(Res.string.cd_progress_edit, progressText)
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.entry_progress_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDecrement, enabled = canDecrement) {
            Icon(AppIcons.Remove, contentDescription = stringResource(Res.string.cd_progress_decrement))
        }
        AppButton(
            text = progressText,
            onClick = onEdit,
            style = AppButtonStyle.Text,
            modifier = Modifier.semantics { contentDescription = editDescription },
        )
        IconButton(onClick = onIncrement) {
            Icon(AppIcons.Add, contentDescription = stringResource(Res.string.cd_progress_increment))
        }
    }
}

@Composable
private fun StatusDialog(
    current: WatchStatus,
    onSelect: (WatchStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    val statuses = WatchStatus.entries
    AppSelectDialog(
        title = stringResource(Res.string.entry_status_label),
        options = statuses.map { stringResource(it.label()) },
        selectedIndex = statuses.indexOf(current),
        onSelect = { onSelect(statuses[it]) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun EntryEditUi.progressText(): String {
    val total = totalEpisodes
    return if (total == null) {
        stringResource(Res.string.entry_progress_value_unknown, progress)
    } else {
        stringResource(Res.string.entry_progress_value, progress, total)
    }
}
