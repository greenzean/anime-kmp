package com.greenzean.animekmp.composeapp.mal

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
private fun MalConnectionNeedsConsentLightPreview() = MalConnectionPreview(MalAccountStatus.NeedsConsent, false)

@Preview
@Composable
private fun MalConnectionNeedsConsentDarkPreview() = MalConnectionPreview(MalAccountStatus.NeedsConsent, true)

@Preview
@Composable
private fun MalConnectionNotConnectedLightPreview() = MalConnectionPreview(MalAccountStatus.NotConnected, false)

@Preview
@Composable
private fun MalConnectionNotConnectedDarkPreview() = MalConnectionPreview(MalAccountStatus.NotConnected, true)

@Preview
@Composable
private fun MalConnectionConnectingLightPreview() = MalConnectionPreview(MalAccountStatus.Connecting, false)

@Preview
@Composable
private fun MalConnectionConnectingDarkPreview() = MalConnectionPreview(MalAccountStatus.Connecting, true)

@Preview
@Composable
private fun MalConnectionConnectedLightPreview() = MalConnectionPreview(MalAccountStatus.Connected("francisco"), false)

@Preview
@Composable
private fun MalConnectionConnectedDarkPreview() = MalConnectionPreview(MalAccountStatus.Connected("francisco"), true)

@Preview
@Composable
private fun MalConnectionErrorLightPreview() =
    MalConnectionPreview(MalAccountStatus.Error(MalAccountError.NoInternet), false)

@Preview
@Composable
private fun MalConnectionErrorDarkPreview() =
    MalConnectionPreview(MalAccountStatus.Error(MalAccountError.NoInternet), true)

@Composable
internal fun MalConnectionPreview(
    status: MalAccountStatus,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) {
        MalConnectionContent(
            state = MalAccountState(status),
            onAction = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
