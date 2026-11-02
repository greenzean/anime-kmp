package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.export.domain.ExportError
import com.greenzean.animekmp.shared.export.presentation.ExportState
import com.greenzean.animekmp.shared.export.presentation.ExportStatus

private const val PREVIEW_COUNT = 42

@Preview
@Composable
private fun LibrarySectionIdleLightPreview() = LibrarySectionPreview(ExportStatus.Idle, darkTheme = false)

@Preview
@Composable
private fun LibrarySectionIdleDarkPreview() = LibrarySectionPreview(ExportStatus.Idle, darkTheme = true)

@Preview
@Composable
private fun LibrarySectionExportingLightPreview() = LibrarySectionPreview(ExportStatus.Exporting, darkTheme = false)

@Preview
@Composable
private fun LibrarySectionExportingDarkPreview() = LibrarySectionPreview(ExportStatus.Exporting, darkTheme = true)

@Preview
@Composable
private fun LibrarySectionDoneLightPreview() =
    LibrarySectionPreview(ExportStatus.Done(PREVIEW_COUNT), darkTheme = false)

@Preview
@Composable
private fun LibrarySectionDoneDarkPreview() = LibrarySectionPreview(ExportStatus.Done(PREVIEW_COUNT), darkTheme = true)

@Preview
@Composable
private fun LibrarySectionEmptyLightPreview() =
    LibrarySectionPreview(ExportStatus.Error(ExportError.EmptyLibrary), darkTheme = false)

@Preview
@Composable
private fun LibrarySectionEmptyDarkPreview() =
    LibrarySectionPreview(ExportStatus.Error(ExportError.EmptyLibrary), darkTheme = true)

@Preview
@Composable
private fun LibrarySectionWriteFailedLightPreview() =
    LibrarySectionPreview(ExportStatus.Error(ExportError.WriteFailed), darkTheme = false)

@Preview
@Composable
private fun LibrarySectionWriteFailedDarkPreview() =
    LibrarySectionPreview(ExportStatus.Error(ExportError.WriteFailed), darkTheme = true)

@Composable
private fun LibrarySectionPreview(
    status: ExportStatus,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) {
        LibrarySection(state = ExportState(status = status), onAction = {})
    }
}
