package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.mal.presentation.MalAccountError
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus

@Preview
@Composable
private fun MalAccountNeedsConsentLightPreview() = MalAccountPreview(MalAccountStatus.NeedsConsent, false)

@Preview
@Composable
private fun MalAccountNeedsConsentDarkPreview() = MalAccountPreview(MalAccountStatus.NeedsConsent, true)

@Preview
@Composable
private fun MalAccountNotConnectedLightPreview() = MalAccountPreview(MalAccountStatus.NotConnected, false)

@Preview
@Composable
private fun MalAccountNotConnectedDarkPreview() = MalAccountPreview(MalAccountStatus.NotConnected, true)

@Preview
@Composable
private fun MalAccountConnectingLightPreview() = MalAccountPreview(MalAccountStatus.Connecting, false)

@Preview
@Composable
private fun MalAccountConnectingDarkPreview() = MalAccountPreview(MalAccountStatus.Connecting, true)

@Composable
internal fun MalAccountPreview(
    status: MalAccountStatus,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) {
        MalAccountSection(
            state = MalAccountState(status),
            onAction = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
