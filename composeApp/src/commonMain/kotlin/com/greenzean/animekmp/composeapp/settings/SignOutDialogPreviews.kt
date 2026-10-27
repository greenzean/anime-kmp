package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.components.AppChoiceDialogActions
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_cancel
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_delete
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_keep
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_title
import com.greenzean.animekmp.shared.auth.presentation.SignOutPrompt
import org.jetbrains.compose.resources.stringResource

@Preview
@Composable
private fun SignOutPreparingLightPreview() = SignOutPreview(SignOutPrompt.Preparing, darkTheme = false)

@Preview
@Composable
private fun SignOutPreparingDarkPreview() = SignOutPreview(SignOutPrompt.Preparing, darkTheme = true)

@Preview
@Composable
private fun SignOutSyncedLightPreview() = SignOutPreview(SignOutPrompt.Ready(unsyncedCount = 0), darkTheme = false)

@Preview
@Composable
private fun SignOutSyncedDarkPreview() = SignOutPreview(SignOutPrompt.Ready(unsyncedCount = 0), darkTheme = true)

@Preview
@Composable
private fun SignOutUnsyncedLightPreview() = SignOutPreview(SignOutPrompt.Ready(unsyncedCount = 3), darkTheme = false)

@Preview
@Composable
private fun SignOutUnsyncedDarkPreview() = SignOutPreview(SignOutPrompt.Ready(unsyncedCount = 3), darkTheme = true)

@Preview
@Composable
private fun SignOutSigningOutLightPreview() = SignOutPreview(SignOutPrompt.SigningOut, darkTheme = false)

@Preview
@Composable
private fun SignOutSigningOutDarkPreview() = SignOutPreview(SignOutPrompt.SigningOut, darkTheme = true)

@Composable
private fun SignOutPreview(
    prompt: SignOutPrompt,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
            ) {
                Text(stringResource(Res.string.sign_out_title), style = MaterialTheme.typography.headlineSmall)
                Text(prompt.message())
                AppChoiceDialogActions(
                    primaryText = stringResource(Res.string.sign_out_keep),
                    onPrimary = {},
                    destructiveText = stringResource(Res.string.sign_out_delete),
                    onDestructive = {},
                    dismissText = stringResource(Res.string.action_cancel),
                    onDismiss = {},
                    busy = prompt !is SignOutPrompt.Ready,
                )
            }
        }
    }
}
