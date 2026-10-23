package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.spacing

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

@Composable
fun AppChoiceDialog(
    title: String,
    text: String,
    primaryText: String,
    onPrimary: () -> Unit,
    destructiveText: String,
    onDestructive: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            ) {
                AppButton(
                    text = primaryText,
                    onClick = onPrimary,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    text = destructiveText,
                    onClick = onDestructive,
                    enabled = !busy,
                    style = AppButtonStyle.Destructive,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    text = dismissText,
                    onClick = onDismiss,
                    enabled = !busy,
                    style = AppButtonStyle.Text,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
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

@Preview
@Composable
private fun AppChoiceDialogLightPreview() = AppChoiceDialogPreview(darkTheme = false)

@Preview
@Composable
private fun AppChoiceDialogDarkPreview() = AppChoiceDialogPreview(darkTheme = true)

@Composable
private fun AppChoiceDialogPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        AppChoiceDialog(
            title = "Remove this?",
            text = "A safe choice, a destructive choice and a cancel.",
            primaryText = "Keep",
            onPrimary = {},
            destructiveText = "Delete",
            onDestructive = {},
            dismissText = "Cancel",
            onDismiss = {},
        )
    }
}
