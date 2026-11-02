package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.components.AppListRow
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_added
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_conflicts
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_import
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_import_error
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_import_progress
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_import_retry
import com.greenzean.animekmp.shared.mal.domain.MalImportStatus
import com.greenzean.animekmp.shared.mal.presentation.MalImportAction
import com.greenzean.animekmp.shared.mal.presentation.MalImportState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val ProgressSize = 24.dp
private val ProgressStrokeWidth = 2.dp
private val MinTouchTarget = 48.dp

@Composable
fun MalImportRow(
    state: MalImportState,
    onAction: (MalImportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowModifier = modifier.testTag(MalImportTags.ROW).semantics { liveRegion = LiveRegionMode.Polite }
    when (val status = state.status) {
        MalImportStatus.Idle, is MalImportStatus.Done -> MalImportReadyRow(status, onAction, rowModifier)
        is MalImportStatus.Importing -> MalImportRunningRow(status, rowModifier)
        MalImportStatus.Error -> MalImportErrorRow(onAction, rowModifier)
    }
}

@Composable
private fun MalImportReadyRow(
    status: MalImportStatus,
    onAction: (MalImportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val supporting = (status as? MalImportStatus.Done)?.let { doneSummary(it) }
    AppListRow(
        title = stringResource(Res.string.settings_mal_import),
        supportingText = supporting,
        onClick = { onAction(MalImportAction.OnImportClick) },
        modifier = modifier,
    )
}

@Composable
private fun doneSummary(done: MalImportStatus.Done): String =
    listOfNotNull(
        pluralStringResource(Res.plurals.settings_import_added, done.added, done.added),
        if (done.conflicts > 0) {
            pluralStringResource(Res.plurals.settings_import_conflicts, done.conflicts, done.conflicts)
        } else {
            null
        },
    ).joinToString("\n")

@Composable
private fun MalImportRunningRow(
    status: MalImportStatus.Importing,
    modifier: Modifier = Modifier,
) {
    val progress = stringResource(Res.string.settings_mal_import_progress, status.done)
    AppListRow(
        title = stringResource(Res.string.settings_mal_import),
        supportingText = progress,
        modifier = modifier,
        trailing = {
            CircularProgressIndicator(
                modifier = Modifier.size(ProgressSize).semantics { contentDescription = progress },
                strokeWidth = ProgressStrokeWidth,
            )
        },
    )
}

@Composable
private fun MalImportErrorRow(
    onAction: (MalImportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MinTouchTarget)
                .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.settings_mal_import_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        AppButton(
            text = stringResource(Res.string.settings_mal_import_retry),
            onClick = { onAction(MalImportAction.OnRetryClick) },
            style = AppButtonStyle.Text,
            modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget).testTag(MalImportTags.RETRY),
        )
    }
}
