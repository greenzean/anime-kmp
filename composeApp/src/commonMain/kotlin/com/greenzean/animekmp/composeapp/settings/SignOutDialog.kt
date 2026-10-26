package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greenzean.animekmp.composeapp.designsystem.components.AppChoiceDialog
import com.greenzean.animekmp.shared.auth.presentation.SignOutPrompt

internal const val SIGN_OUT_TITLE = "Sign out"
internal const val SIGN_OUT_KEEP = "Keep library on this device"
internal const val SIGN_OUT_DELETE = "Delete library from this device"
internal const val SIGN_OUT_CANCEL = "Cancel"

@Composable
fun SignOutDialog(
    prompt: SignOutPrompt,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppChoiceDialog(
        title = SIGN_OUT_TITLE,
        text = prompt.message(),
        primaryText = SIGN_OUT_KEEP,
        onPrimary = onKeep,
        destructiveText = SIGN_OUT_DELETE,
        onDestructive = onDelete,
        dismissText = SIGN_OUT_CANCEL,
        onDismiss = onDismiss,
        modifier = modifier,
        busy = prompt !is SignOutPrompt.Ready,
    )
}

internal fun SignOutPrompt.message(): String =
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
