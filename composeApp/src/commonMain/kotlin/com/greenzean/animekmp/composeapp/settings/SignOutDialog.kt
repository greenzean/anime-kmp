package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greenzean.animekmp.composeapp.designsystem.components.AppChoiceDialog
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_cancel
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_delete
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_keep
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_preparing
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_ready
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_ready_with_unsynced
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_signing_out
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_title
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_unsynced
import com.greenzean.animekmp.shared.auth.presentation.SignOutPrompt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SignOutDialog(
    prompt: SignOutPrompt,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppChoiceDialog(
        title = stringResource(Res.string.sign_out_title),
        text = prompt.message(),
        primaryText = stringResource(Res.string.sign_out_keep),
        onPrimary = onKeep,
        destructiveText = stringResource(Res.string.sign_out_delete),
        onDestructive = onDelete,
        dismissText = stringResource(Res.string.action_cancel),
        onDismiss = onDismiss,
        modifier = modifier,
        busy = prompt !is SignOutPrompt.Ready,
    )
}

@Composable
internal fun SignOutPrompt.message(): String =
    when (this) {
        SignOutPrompt.Preparing -> {
            stringResource(Res.string.sign_out_preparing)
        }

        SignOutPrompt.SigningOut -> {
            stringResource(Res.string.sign_out_signing_out)
        }

        is SignOutPrompt.Ready -> {
            val base = stringResource(Res.string.sign_out_ready)
            if (unsyncedCount > 0) {
                val unsynced = pluralStringResource(Res.plurals.sign_out_unsynced, unsyncedCount, unsyncedCount)
                stringResource(Res.string.sign_out_ready_with_unsynced, base, unsynced)
            } else {
                base
            }
        }
    }
