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
import com.greenzean.animekmp.composeapp.conflicts.ConflictsTags
import com.greenzean.animekmp.composeapp.designsystem.components.AppCard
import com.greenzean.animekmp.composeapp.designsystem.components.AppListRow
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_export_mal
import com.greenzean.animekmp.composeapp.generated.resources.cd_import_mal
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_empty_title
import com.greenzean.animekmp.composeapp.generated.resources.settings_conflicts_row
import com.greenzean.animekmp.composeapp.generated.resources.settings_conflicts_supporting
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_done
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_error_empty
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_error_write
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_exporting
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_mal
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_added
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_conflicts
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_error_not_mal
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_error_unreadable
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_importing
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_invalid
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_mal
import com.greenzean.animekmp.composeapp.generated.resources.settings_library_title
import com.greenzean.animekmp.shared.export.domain.ExportError
import com.greenzean.animekmp.shared.export.presentation.ExportAction
import com.greenzean.animekmp.shared.export.presentation.ExportState
import com.greenzean.animekmp.shared.export.presentation.ExportStatus
import com.greenzean.animekmp.shared.importer.domain.ImportError
import com.greenzean.animekmp.shared.importer.presentation.ImportFileAction
import com.greenzean.animekmp.shared.importer.presentation.ImportFileState
import com.greenzean.animekmp.shared.importer.presentation.ImportStatus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val ProgressSize = 24.dp
private val ProgressStrokeWidth = 2.dp

@Composable
fun LibrarySection(
    state: ExportState,
    onAction: (ExportAction) -> Unit,
    importState: ImportFileState,
    onImportAction: (ImportFileAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val importing = importState.status is ImportStatus.Importing
    val importDescription = stringResource(Res.string.cd_import_mal)
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
            AppListRow(
                title = stringResource(Res.string.settings_import_mal),
                onClick = if (importing) null else ({ onImportAction(ImportFileAction.OnImportClick) }),
                modifier =
                    Modifier
                        .testTag(ImportTags.ROW)
                        .semantics { contentDescription = importDescription },
                trailing =
                    (importState.status as? ImportStatus.Importing)?.let { status ->
                        { ImportProgress(progress = status.progress) }
                    },
            )
            ImportStatusMessage(status = importState.status, pendingConflicts = importState.conflictCount)
            AppListRow(
                title = stringResource(Res.string.settings_conflicts_row),
                supportingText =
                    if (importState.conflictCount > 0) {
                        pluralStringResource(
                            Res.plurals.settings_conflicts_supporting,
                            importState.conflictCount,
                            importState.conflictCount,
                        )
                    } else {
                        stringResource(Res.string.conflicts_empty_title)
                    },
                onClick = { onImportAction(ImportFileAction.OnConflictsClick) },
                modifier = Modifier.testTag(ConflictsTags.SETTINGS_ROW),
            )
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
private fun ImportProgress(progress: Float) {
    val description = stringResource(Res.string.settings_import_importing)
    CircularProgressIndicator(
        progress = { progress },
        modifier =
            Modifier
                .size(ProgressSize)
                .testTag(ImportTags.PROGRESS)
                .semantics { contentDescription = description },
        strokeWidth = ProgressStrokeWidth,
    )
}

@Composable
private fun ImportStatusMessage(
    status: ImportStatus,
    pendingConflicts: Int,
) {
    when (status) {
        is ImportStatus.Done -> {
            val conflicts = minOf(status.conflicts, pendingConflicts)
            val lines =
                listOfNotNull(
                    pluralStringResource(Res.plurals.settings_import_added, status.added, status.added),
                    if (conflicts > 0) {
                        pluralStringResource(Res.plurals.settings_import_conflicts, conflicts, conflicts)
                    } else {
                        null
                    },
                    if (status.invalid > 0) {
                        pluralStringResource(Res.plurals.settings_import_invalid, status.invalid, status.invalid)
                    } else {
                        null
                    },
                )
            Text(
                lines.joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm)
                        .testTag(ImportTags.DONE)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        is ImportStatus.Error -> {
            Text(
                stringResource(
                    when (status.reason) {
                        ImportError.Unreadable -> Res.string.settings_import_error_unreadable
                        ImportError.NotMalExport -> Res.string.settings_import_error_not_mal
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm)
                        .testTag(ImportTags.ERROR)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        ImportStatus.Idle, is ImportStatus.Importing -> {
            Unit
        }
    }
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
