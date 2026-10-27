package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.greenzean.animekmp.composeapp.designsystem.components.AppBanner
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_cancel
import com.greenzean.animekmp.composeapp.generated.resources.action_try_again
import com.greenzean.animekmp.composeapp.generated.resources.delete_account_confirm_with_google
import com.greenzean.animekmp.composeapp.generated.resources.delete_account_deleted
import com.greenzean.animekmp.composeapp.generated.resources.delete_account_kept
import com.greenzean.animekmp.composeapp.generated.resources.delete_account_permanent
import com.greenzean.animekmp.composeapp.generated.resources.delete_account_title
import com.greenzean.animekmp.shared.auth.presentation.DeletePrompt
import org.jetbrains.compose.resources.stringResource

@Composable
fun DeleteAccountDialog(
    prompt: DeletePrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        title = { DeleteAccountTitle() },
        text = { DeleteAccountBody(prompt) },
        confirmButton = { DeleteAccountActions(prompt, onConfirm, onDismiss) },
    )
}

@Composable
internal fun DeleteAccountTitle() {
    Text(stringResource(Res.string.delete_account_title), modifier = Modifier.semantics { heading() })
}

@Composable
internal fun DeleteAccountBody(prompt: DeletePrompt) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
        Text(stringResource(Res.string.delete_account_permanent))
        Text(stringResource(Res.string.delete_account_deleted))
        Text(stringResource(Res.string.delete_account_kept))
        Text(stringResource(Res.string.delete_account_confirm_with_google))
        if (prompt is DeletePrompt.Failed) {
            AppBanner(message = prompt.error.toDeleteUiText().asString())
        }
    }
}

@Composable
internal fun DeleteAccountActions(
    prompt: DeletePrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val deleting = prompt == DeletePrompt.Deleting
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
    ) {
        AppButton(
            text =
                stringResource(
                    if (prompt is DeletePrompt.Failed) Res.string.action_try_again else Res.string.delete_account_title,
                ),
            onClick = onConfirm,
            style = AppButtonStyle.Destructive,
            loading = deleting,
            modifier = Modifier.fillMaxWidth(),
        )
        AppButton(
            text = stringResource(Res.string.action_cancel),
            onClick = onDismiss,
            style = AppButtonStyle.Text,
            enabled = !deleting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
