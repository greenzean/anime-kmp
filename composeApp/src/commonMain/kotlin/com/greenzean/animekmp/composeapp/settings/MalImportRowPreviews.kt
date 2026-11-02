package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.mal.domain.MalImportStatus
import com.greenzean.animekmp.shared.mal.presentation.MalImportState

private const val PREVIEW_DONE = 42
private const val PREVIEW_ADDED = 120
private const val PREVIEW_CONFLICTS = 3

@Preview
@Composable
private fun MalImportIdleLightPreview() = MalImportPreview(MalImportStatus.Idle, false)

@Preview
@Composable
private fun MalImportIdleDarkPreview() = MalImportPreview(MalImportStatus.Idle, true)

@Preview
@Composable
private fun MalImportImportingLightPreview() = MalImportPreview(MalImportStatus.Importing(PREVIEW_DONE), false)

@Preview
@Composable
private fun MalImportImportingDarkPreview() = MalImportPreview(MalImportStatus.Importing(PREVIEW_DONE), true)

@Preview
@Composable
private fun MalImportDoneLightPreview() =
    MalImportPreview(MalImportStatus.Done(added = PREVIEW_ADDED, conflicts = PREVIEW_CONFLICTS), false)

@Preview
@Composable
private fun MalImportDoneDarkPreview() =
    MalImportPreview(MalImportStatus.Done(added = PREVIEW_ADDED, conflicts = PREVIEW_CONFLICTS), true)

@Preview
@Composable
private fun MalImportErrorLightPreview() = MalImportPreview(MalImportStatus.Error, false)

@Preview
@Composable
private fun MalImportErrorDarkPreview() = MalImportPreview(MalImportStatus.Error, true)

@Composable
private fun MalImportPreview(
    status: MalImportStatus,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) {
        Column { MalImportRow(state = MalImportState(status), onAction = {}) }
    }
}
