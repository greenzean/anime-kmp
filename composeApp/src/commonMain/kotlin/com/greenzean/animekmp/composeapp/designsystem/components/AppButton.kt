package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.MinTouchTarget
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_add_to_library
import com.greenzean.animekmp.composeapp.generated.resources.cd_in_progress
import com.greenzean.animekmp.composeapp.generated.resources.preview_button_destructive
import com.greenzean.animekmp.composeapp.generated.resources.preview_button_disabled
import com.greenzean.animekmp.composeapp.generated.resources.preview_button_loading
import com.greenzean.animekmp.composeapp.generated.resources.preview_button_outlined
import com.greenzean.animekmp.composeapp.generated.resources.preview_button_text
import com.greenzean.animekmp.composeapp.generated.resources.preview_button_tonal
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Filled,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val inProgress = stringResource(Res.string.cd_in_progress, text)
    val buttonModifier =
        modifier
            .defaultMinSize(minHeight = MinTouchTarget)
            .semantics { if (loading) contentDescription = inProgress }
    val active = enabled && !loading
    val content: @Composable () -> Unit = { ButtonContent(text = text, loading = loading) }
    when (style) {
        AppButtonStyle.Filled -> {
            Button(onClick, buttonModifier, enabled = active) { content() }
        }

        AppButtonStyle.Tonal -> {
            FilledTonalButton(onClick, buttonModifier, enabled = active) { content() }
        }

        AppButtonStyle.Outlined -> {
            OutlinedButton(onClick, buttonModifier, enabled = active) { content() }
        }

        AppButtonStyle.Text -> {
            TextButton(onClick, buttonModifier, enabled = active) { content() }
        }

        AppButtonStyle.Destructive -> {
            TextButton(
                onClick,
                buttonModifier,
                enabled = active,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { content() }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    loading: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = ButtonDefaults.buttonColors().contentColor,
            )
        } else {
            Text(text)
        }
    }
}

@Preview
@Composable
private fun AppButtonLightPreview() = AppButtonPreview(darkTheme = false)

@Preview
@Composable
private fun AppButtonDarkPreview() = AppButtonPreview(darkTheme = true)

@Composable
private fun AppButtonPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppButton(stringResource(Res.string.action_add_to_library), onClick = {})
            AppButton(stringResource(Res.string.preview_button_tonal), onClick = {}, style = AppButtonStyle.Tonal)
            AppButton(stringResource(Res.string.preview_button_outlined), onClick = {}, style = AppButtonStyle.Outlined)
            AppButton(stringResource(Res.string.preview_button_text), onClick = {}, style = AppButtonStyle.Text)
            AppButton(
                stringResource(Res.string.preview_button_destructive),
                onClick = {},
                style = AppButtonStyle.Destructive,
            )
            AppButton(stringResource(Res.string.preview_button_loading), onClick = {}, loading = true)
            AppButton(stringResource(Res.string.preview_button_disabled), onClick = {}, enabled = false)
        }
    }
}
