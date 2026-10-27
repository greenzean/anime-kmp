package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_dismiss
import com.greenzean.animekmp.composeapp.generated.resources.action_retry
import com.greenzean.animekmp.composeapp.generated.resources.preview_something_went_wrong
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppBanner(
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                .padding(start = spacing.lg, top = spacing.md, end = spacing.sm)
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(content = actions)
    }
}

@Preview
@Composable
private fun AppBannerLightPreview() = AppBannerPreview(darkTheme = false)

@Preview
@Composable
private fun AppBannerDarkPreview() = AppBannerPreview(darkTheme = true)

@Composable
private fun AppBannerPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        AppBanner(
            message = stringResource(Res.string.preview_something_went_wrong),
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
        ) {
            AppButton(text = stringResource(Res.string.action_dismiss), onClick = {}, style = AppButtonStyle.Text)
            AppButton(text = stringResource(Res.string.action_retry), onClick = {}, style = AppButtonStyle.Text)
        }
    }
}
