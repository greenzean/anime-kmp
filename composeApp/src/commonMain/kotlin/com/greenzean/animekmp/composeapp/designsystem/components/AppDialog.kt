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
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_cancel
import com.greenzean.animekmp.composeapp.generated.resources.preview_choice_destructive
import com.greenzean.animekmp.composeapp.generated.resources.preview_choice_primary
import com.greenzean.animekmp.composeapp.generated.resources.preview_choice_text
import com.greenzean.animekmp.composeapp.generated.resources.preview_choice_title
import com.greenzean.animekmp.composeapp.generated.resources.preview_dialog_confirm
import com.greenzean.animekmp.composeapp.generated.resources.preview_dialog_dismiss
import com.greenzean.animekmp.composeapp.generated.resources.preview_dialog_text
import com.greenzean.animekmp.composeapp.generated.resources.preview_dialog_title
import org.jetbrains.compose.resources.stringResource

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
            AppChoiceDialogActions(
                primaryText = primaryText,
                onPrimary = onPrimary,
                destructiveText = destructiveText,
                onDestructive = onDestructive,
                dismissText = dismissText,
                onDismiss = onDismiss,
                busy = busy,
            )
        },
    )
}

@Composable
internal fun AppChoiceDialogActions(
    primaryText: String,
    onPrimary: () -> Unit,
    destructiveText: String,
    onDestructive: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    busy: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
    ) {
        AppButton(text = primaryText, onClick = onPrimary, enabled = !busy, modifier = Modifier.fillMaxWidth())
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
            title = stringResource(Res.string.preview_dialog_title),
            text = stringResource(Res.string.preview_dialog_text),
            confirmText = stringResource(Res.string.preview_dialog_confirm),
            onConfirm = {},
            dismissText = stringResource(Res.string.preview_dialog_dismiss),
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
            title = stringResource(Res.string.preview_choice_title),
            text = stringResource(Res.string.preview_choice_text),
            primaryText = stringResource(Res.string.preview_choice_primary),
            onPrimary = {},
            destructiveText = stringResource(Res.string.preview_choice_destructive),
            onDestructive = {},
            dismissText = stringResource(Res.string.action_cancel),
            onDismiss = {},
        )
    }
}
