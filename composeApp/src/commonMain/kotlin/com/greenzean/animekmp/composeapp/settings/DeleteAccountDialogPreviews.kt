package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.shared.auth.domain.SignInError
import com.greenzean.animekmp.shared.auth.presentation.DeletePrompt
import com.greenzean.animekmp.shared.core.domain.DataError

@Preview
@Composable
private fun DeleteConfirmingLightPreview() = DeletePreview(DeletePrompt.Confirming, darkTheme = false)

@Preview
@Composable
private fun DeleteConfirmingDarkPreview() = DeletePreview(DeletePrompt.Confirming, darkTheme = true)

@Preview
@Composable
private fun DeleteDeletingLightPreview() = DeletePreview(DeletePrompt.Deleting, darkTheme = false)

@Preview
@Composable
private fun DeleteDeletingDarkPreview() = DeletePreview(DeletePrompt.Deleting, darkTheme = true)

@Preview
@Composable
private fun DeleteFailedLightPreview() = DeletePreview(failed(), darkTheme = false)

@Preview
@Composable
private fun DeleteFailedDarkPreview() = DeletePreview(failed(), darkTheme = true)

private fun failed() = DeletePrompt.Failed(SignInError.Server(DataError.Network.NO_INTERNET))

@Composable
private fun DeletePreview(
    prompt: DeletePrompt,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
            ) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall) { DeleteAccountTitle() }
                DeleteAccountBody(prompt)
                DeleteAccountActions(prompt, onConfirm = {}, onDismiss = {})
            }
        }
    }
}
