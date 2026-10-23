package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.auth.presentation.SignOutPrompt

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
    ThemedPreview(darkTheme) { SignOutDialog(prompt = prompt, onKeep = {}, onDelete = {}, onDismiss = {}) }
}
