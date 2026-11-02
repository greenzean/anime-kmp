package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.export.presentation.ExportState
import com.greenzean.animekmp.shared.importer.domain.ImportError
import com.greenzean.animekmp.shared.importer.presentation.ImportFileState
import com.greenzean.animekmp.shared.importer.presentation.ImportStatus

private const val PREVIEW_PROGRESS = 0.6f
private const val PREVIEW_COUNT = 42

@Preview
@Composable
private fun LibrarySectionImportIdleLightPreview() = ImportPreview(ImportStatus.Idle, darkTheme = false)

@Preview
@Composable
private fun LibrarySectionImportIdleDarkPreview() = ImportPreview(ImportStatus.Idle, darkTheme = true)

@Preview
@Composable
private fun LibrarySectionImportingLightPreview() =
    ImportPreview(ImportStatus.Importing(PREVIEW_PROGRESS), darkTheme = false)

@Preview
@Composable
private fun LibrarySectionImportingDarkPreview() =
    ImportPreview(ImportStatus.Importing(PREVIEW_PROGRESS), darkTheme = true)

@Preview
@Composable
private fun LibrarySectionImportDoneLightPreview() =
    ImportPreview(ImportStatus.Done(added = PREVIEW_COUNT, keptYours = 3, invalid = 1), darkTheme = false)

@Preview
@Composable
private fun LibrarySectionImportDoneDarkPreview() =
    ImportPreview(ImportStatus.Done(added = PREVIEW_COUNT, keptYours = 3, invalid = 1), darkTheme = true)

@Preview
@Composable
private fun LibrarySectionImportUnreadableLightPreview() =
    ImportPreview(ImportStatus.Error(ImportError.Unreadable), darkTheme = false)

@Preview
@Composable
private fun LibrarySectionImportUnreadableDarkPreview() =
    ImportPreview(ImportStatus.Error(ImportError.Unreadable), darkTheme = true)

@Preview
@Composable
private fun LibrarySectionImportNotMalLightPreview() =
    ImportPreview(ImportStatus.Error(ImportError.NotMalExport), darkTheme = false)

@Preview
@Composable
private fun LibrarySectionImportNotMalDarkPreview() =
    ImportPreview(ImportStatus.Error(ImportError.NotMalExport), darkTheme = true)

@Composable
private fun ImportPreview(
    status: ImportStatus,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) {
        LibrarySection(
            state = ExportState(),
            onAction = {},
            importState = ImportFileState(status = status),
            onImportAction = {},
        )
    }
}
