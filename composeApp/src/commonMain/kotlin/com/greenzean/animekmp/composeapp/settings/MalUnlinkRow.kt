package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenzean.animekmp.composeapp.designsystem.MinTouchTarget
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.components.AppDialog
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_cancel
import com.greenzean.animekmp.composeapp.generated.resources.mal_unlink_dialog_confirm
import com.greenzean.animekmp.composeapp.generated.resources.mal_unlink_dialog_message
import com.greenzean.animekmp.composeapp.generated.resources.mal_unlink_dialog_title
import com.greenzean.animekmp.composeapp.generated.resources.mal_unlink_row
import com.greenzean.animekmp.composeapp.mal.MalAccountTags
import org.jetbrains.compose.resources.stringResource

@Composable
fun MalUnlinkRow(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyConfirming: Boolean = false,
) {
    var confirming by remember { mutableStateOf(initiallyConfirming) }
    Box(modifier = modifier) {
        AppButton(
            text = stringResource(Res.string.mal_unlink_row),
            onClick = { confirming = true },
            style = AppButtonStyle.Outlined,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(
                        minHeight = MinTouchTarget,
                    ).testTag(MalAccountTags.UNLINK),
        )
        if (confirming) {
            AppDialog(
                title = stringResource(Res.string.mal_unlink_dialog_title),
                text = stringResource(Res.string.mal_unlink_dialog_message),
                confirmText = stringResource(Res.string.mal_unlink_dialog_confirm),
                onConfirm = {
                    confirming = false
                    onConfirm()
                },
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = { confirming = false },
                confirmModifier = Modifier.testTag(MalAccountTags.UNLINK_CONFIRM),
            )
        }
    }
}
