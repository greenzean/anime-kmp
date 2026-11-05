package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus

@Preview
@Composable
private fun MalLinkedToAccountLightPreview() = MalLinkedToAccountPreview(darkTheme = false)

@Preview
@Composable
private fun MalLinkedToAccountDarkPreview() = MalLinkedToAccountPreview(darkTheme = true)

@Preview
@Composable
private fun MalUnlinkDialogLightPreview() = MalUnlinkDialogPreview(darkTheme = false)

@Preview
@Composable
private fun MalUnlinkDialogDarkPreview() = MalUnlinkDialogPreview(darkTheme = true)

@Preview
@Composable
private fun MalUnlinkFailedLightPreview() = MalUnlinkFailedPreview(darkTheme = false)

@Preview
@Composable
private fun MalUnlinkFailedDarkPreview() = MalUnlinkFailedPreview(darkTheme = true)

@Composable
private fun MalUnlinkFailedPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        MalAccountSection(
            state =
                MalAccountState(
                    status = MalAccountStatus.NotConnected,
                    linkedElsewhere = true,
                    linkedToAccount = true,
                    unlinkFailed = true,
                ),
            onAction = {},
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
        )
    }
}

@Composable
private fun MalLinkedToAccountPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        MalAccountSection(
            state =
                MalAccountState(
                    status = MalAccountStatus.NotConnected,
                    linkedElsewhere = true,
                    linkedToAccount = true,
                ),
            onAction = {},
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
        )
    }
}

@Composable
private fun MalUnlinkDialogPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        MalUnlinkRow(onConfirm = {}, initiallyConfirming = true)
    }
}
