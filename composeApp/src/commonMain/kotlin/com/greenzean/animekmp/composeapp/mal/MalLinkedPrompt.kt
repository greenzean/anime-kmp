package com.greenzean.animekmp.composeapp.mal

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenzean.animekmp.composeapp.designsystem.MinTouchTarget
import com.greenzean.animekmp.composeapp.designsystem.components.AppBanner
import com.greenzean.animekmp.composeapp.designsystem.components.AppBannerTone
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.mal_linked_connect
import com.greenzean.animekmp.composeapp.generated.resources.mal_linked_not_here
import com.greenzean.animekmp.composeapp.generated.resources.mal_linked_prompt
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MalLinkedPrompt(
    onNotHere: () -> Unit,
    promptTag: String,
    notHereTag: String,
    modifier: Modifier = Modifier,
    onConnect: (() -> Unit)? = null,
    connectTag: String? = null,
) {
    AppBanner(
        message = stringResource(Res.string.mal_linked_prompt),
        modifier = modifier.testTag(promptTag),
        tone = AppBannerTone.Info,
    ) {
        AppButton(
            text = stringResource(Res.string.mal_linked_not_here),
            onClick = onNotHere,
            style = AppButtonStyle.Text,
            modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget).testTag(notHereTag),
        )
        if (onConnect != null && connectTag != null) {
            AppButton(
                text = stringResource(Res.string.mal_linked_connect),
                onClick = onConnect,
                style = AppButtonStyle.Text,
                modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget).testTag(connectTag),
            )
        }
    }
}
