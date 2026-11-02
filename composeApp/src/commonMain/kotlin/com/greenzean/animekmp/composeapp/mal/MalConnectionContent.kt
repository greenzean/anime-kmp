package com.greenzean.animekmp.composeapp.mal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.greenzean.animekmp.composeapp.designsystem.components.AppBanner
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_dismiss
import com.greenzean.animekmp.composeapp.generated.resources.action_retry
import com.greenzean.animekmp.composeapp.generated.resources.mal_connect
import com.greenzean.animekmp.composeapp.generated.resources.mal_connected_as
import com.greenzean.animekmp.composeapp.generated.resources.mal_connected_no_name
import com.greenzean.animekmp.composeapp.generated.resources.mal_connecting
import com.greenzean.animekmp.composeapp.generated.resources.mal_consent_accept
import com.greenzean.animekmp.composeapp.generated.resources.mal_consent_body
import com.greenzean.animekmp.composeapp.generated.resources.mal_disconnect
import com.greenzean.animekmp.shared.mal.presentation.MalAccountAction
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus
import org.jetbrains.compose.resources.stringResource

@Composable
fun MalConnectionContent(
    state: MalAccountState,
    onAction: (MalAccountAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
        when (val status = state.status) {
            MalAccountStatus.NeedsConsent -> {
                NeedsConsent(onAction)
            }

            MalAccountStatus.NotConnected -> {
                ConnectButton(onAction)
            }

            MalAccountStatus.Connecting -> {
                Connecting()
            }

            is MalAccountStatus.Connected -> {
                Connected(status, onAction)
            }

            is MalAccountStatus.Error -> {
                AppBanner(
                    message = status.reason.toUiText().asString(),
                    modifier = Modifier.testTag(MalAccountTags.ERROR),
                ) {
                    AppButton(
                        text = stringResource(Res.string.action_dismiss),
                        onClick = { onAction(MalAccountAction.OnErrorDismissed) },
                        style = AppButtonStyle.Text,
                    )
                    AppButton(
                        text = stringResource(Res.string.action_retry),
                        onClick = { onAction(MalAccountAction.OnConnectClick) },
                        style = AppButtonStyle.Text,
                        modifier = Modifier.testTag(MalAccountTags.RETRY),
                    )
                }
            }
        }
    }
}

@Composable
private fun NeedsConsent(onAction: (MalAccountAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
        Text(
            stringResource(Res.string.mal_consent_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        AppButton(
            text = stringResource(Res.string.mal_consent_accept),
            onClick = { onAction(MalAccountAction.OnConsentAccepted) },
            modifier = Modifier.fillMaxWidth().testTag(MalAccountTags.CONSENT_ACCEPT),
        )
    }
}

@Composable
private fun ConnectButton(onAction: (MalAccountAction) -> Unit) {
    AppButton(
        text = stringResource(Res.string.mal_connect),
        onClick = { onAction(MalAccountAction.OnConnectClick) },
        modifier = Modifier.fillMaxWidth().testTag(MalAccountTags.CONNECT),
    )
}

@Composable
private fun Connecting() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(Res.string.mal_connecting),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(MalAccountTags.STATUS),
        )
    }
}

@Composable
private fun Connected(
    status: MalAccountStatus.Connected,
    onAction: (MalAccountAction) -> Unit,
) {
    val username = status.username
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
        Text(
            if (username == null) {
                stringResource(Res.string.mal_connected_no_name)
            } else {
                stringResource(Res.string.mal_connected_as, username)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(MalAccountTags.STATUS),
        )
        AppButton(
            text = stringResource(Res.string.mal_disconnect),
            onClick = { onAction(MalAccountAction.OnDisconnectClick) },
            style = AppButtonStyle.Outlined,
            modifier = Modifier.fillMaxWidth().testTag(MalAccountTags.DISCONNECT),
        )
    }
}
