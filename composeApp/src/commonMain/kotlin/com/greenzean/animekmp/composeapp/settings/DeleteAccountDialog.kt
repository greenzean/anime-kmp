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
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.auth.domain.SignInError
import com.greenzean.animekmp.shared.auth.presentation.DeletePrompt
import com.greenzean.animekmp.shared.core.domain.DataError

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
    Text("Delete account", modifier = Modifier.semantics { heading() })
}

@Composable
internal fun DeleteAccountBody(prompt: DeletePrompt) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
        Text("This permanently deletes your account and cannot be undone.")
        Text(
            "Deleted from our server: your account, your synced library and every sign-in session on all " +
                "your devices.",
        )
        Text("Kept: the copy of your library on this device. You can keep using the app without an account.")
        Text("You will confirm with Google to prove the account is yours.")
        if (prompt is DeletePrompt.Failed) {
            AppBanner(message = prompt.error.deleteMessage())
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
            text = if (prompt is DeletePrompt.Failed) "Try again" else "Delete account",
            onClick = onConfirm,
            style = AppButtonStyle.Destructive,
            loading = deleting,
            modifier = Modifier.fillMaxWidth(),
        )
        AppButton(
            text = "Cancel",
            onClick = onDismiss,
            style = AppButtonStyle.Text,
            enabled = !deleting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun SignInError.deleteMessage(): String =
    when (this) {
        is SignInError.Google -> {
            when (reason) {
                GoogleSignInError.NO_CREDENTIAL -> "No Google account was found on this device."
                GoogleSignInError.NOT_CONFIGURED -> "Sign-in is not set up in this build."
                GoogleSignInError.CANCELLED, GoogleSignInError.UNKNOWN -> "Google sign-in failed. Try again."
            }
        }

        is SignInError.Server -> {
            when (reason) {
                DataError.Network.NO_INTERNET, DataError.Network.REQUEST_TIMEOUT -> "No connection. Try again online."
                DataError.Network.UNAUTHORIZED -> "Choose the Google account you signed in with and try again."
                else -> "Could not reach the server. Try again later."
            }
        }
    }
