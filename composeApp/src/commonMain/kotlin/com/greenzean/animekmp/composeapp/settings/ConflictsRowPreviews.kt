package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.export.presentation.ExportState
import com.greenzean.animekmp.shared.importer.presentation.ImportFileState

private const val PREVIEW_CONFLICTS = 3

@Preview
@Composable
private fun LibrarySectionConflictsRowLightPreview() = ConflictsRowPreview(darkTheme = false)

@Preview
@Composable
private fun LibrarySectionConflictsRowDarkPreview() = ConflictsRowPreview(darkTheme = true)

@Composable
private fun ConflictsRowPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        LibrarySection(
            state = ExportState(),
            onAction = {},
            importState = ImportFileState(conflictCount = PREVIEW_CONFLICTS),
            onImportAction = {},
        )
    }
}
