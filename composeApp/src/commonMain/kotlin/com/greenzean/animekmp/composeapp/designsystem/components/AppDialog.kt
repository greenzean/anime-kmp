package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview

/** Confirmation dialog with a confirm and a dismiss action (both text buttons, 48dp targets). */
@Composable
fun AppDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { AppButton(text = confirmText, onClick = onConfirm, style = AppButtonStyle.Text) },
        dismissButton = { AppButton(text = dismissText, onClick = onDismiss, style = AppButtonStyle.Text) },
    )
}

@Preview
@Composable
private fun AppDialogLightPreview() = AppDialogPreview(darkTheme = false)

@Preview
@Composable
private fun AppDialogDarkPreview() = AppDialogPreview(darkTheme = true)

@Composable
private fun AppDialogPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        AppDialog(
            title = "Discard changes?",
            text = "Your edits have not been saved to the library.",
            confirmText = "Discard",
            onConfirm = {},
            dismissText = "Keep editing",
            onDismiss = {},
        )
    }
}
