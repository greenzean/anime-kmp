package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greenzean.animekmp.composeapp.designsystem.components.AppChoiceDialog
import com.greenzean.animekmp.shared.auth.presentation.SignOutPrompt

@Composable
fun SignOutDialog(
    prompt: SignOutPrompt,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppChoiceDialog(
        title = "Sign out",
        text = prompt.message(),
        primaryText = "Keep library on this device",
        onPrimary = onKeep,
        destructiveText = "Delete library from this device",
        onDestructive = onDelete,
        dismissText = "Cancel",
        onDismiss = onDismiss,
        modifier = modifier,
        busy = prompt !is SignOutPrompt.Ready,
    )
}

private fun SignOutPrompt.message(): String =
    when (this) {
        SignOutPrompt.Preparing -> {
            "Syncing your latest changes before signing out."
        }

        SignOutPrompt.SigningOut -> {
            "Signing out."
        }

        is SignOutPrompt.Ready -> {
            val base = "Your library stays in your account either way. Keep it here or delete it from this device."
            if (unsyncedCount > 0) {
                val changes = if (unsyncedCount == 1) "change is" else "changes are"
                "$base $unsyncedCount $changes not synced yet and would be lost if you delete."
            } else {
                base
            }
        }
    }
