package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.components.AppCard
import com.greenzean.animekmp.composeapp.designsystem.components.AppListRow
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_export_mal
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_done
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_error_empty
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_error_write
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_exporting
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_mal
import com.greenzean.animekmp.composeapp.generated.resources.settings_library_title
import com.greenzean.animekmp.shared.export.domain.ExportError
import com.greenzean.animekmp.shared.export.presentation.ExportAction
import com.greenzean.animekmp.shared.export.presentation.ExportState
import com.greenzean.animekmp.shared.export.presentation.ExportStatus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val ProgressSize = 24.dp
private val ProgressStrokeWidth = 2.dp

@Composable
fun LibrarySection(
    state: ExportState,
    onAction: (ExportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exporting = state.status is ExportStatus.Exporting
    val exportDescription = stringResource(Res.string.cd_export_mal)
    Column(modifier = modifier) {
        Text(
            stringResource(Res.string.settings_library_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs).semantics { heading() },
        )
        AppCard(modifier = Modifier.padding(top = MaterialTheme.spacing.md)) {
            AppListRow(
                title = stringResource(Res.string.settings_export_mal),
                onClick = if (exporting) null else ({ onAction(ExportAction.OnExportClick) }),
                modifier =
                    Modifier
                        .testTag(ExportTags.ROW)
                        .semantics { contentDescription = exportDescription },
                trailing =
                    if (exporting) {
                        { ExportProgress() }
                    } else {
                        null
                    },
            )
            ExportStatusMessage(status = state.status)
        }
    }
}

@Composable
private fun ExportProgress() {
    val description = stringResource(Res.string.settings_export_exporting)
    CircularProgressIndicator(
        modifier =
            Modifier
                .size(ProgressSize)
                .testTag(ExportTags.PROGRESS)
                .semantics { contentDescription = description },
        strokeWidth = ProgressStrokeWidth,
    )
}

@Composable
private fun ExportStatusMessage(status: ExportStatus) {
    when (status) {
        is ExportStatus.Done -> {
            Text(
                pluralStringResource(Res.plurals.settings_export_done, status.count, status.count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm)
                        .testTag(ExportTags.DONE)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        is ExportStatus.Error -> {
            Text(
                stringResource(
                    when (status.reason) {
                        ExportError.EmptyLibrary -> Res.string.settings_export_error_empty
                        ExportError.WriteFailed -> Res.string.settings_export_error_write
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm)
                        .testTag(ExportTags.ERROR)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        ExportStatus.Idle, ExportStatus.Exporting -> {
            Unit
        }
    }
}
