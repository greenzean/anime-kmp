package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_retry
import com.greenzean.animekmp.composeapp.generated.resources.error_something_went_wrong
import com.greenzean.animekmp.composeapp.generated.resources.preview_empty_action
import com.greenzean.animekmp.composeapp.generated.resources.preview_empty_message
import com.greenzean.animekmp.composeapp.generated.resources.preview_empty_title
import com.greenzean.animekmp.composeapp.generated.resources.preview_error_message
import org.jetbrains.compose.resources.stringResource

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = AppIcons.List,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
) {
    StateLayout(
        actionModifier = actionModifier,
        title = title,
        message = message,
        icon = icon,
        actionText = actionText,
        onAction = onAction,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.error_something_went_wrong),
    retryText: String = stringResource(Res.string.action_retry),
    onRetry: (() -> Unit)? = null,
) {
    StateLayout(
        title = title,
        message = message,
        icon = AppIcons.Warning,
        actionText = retryText.takeIf { onRetry != null },
        onAction = onRetry,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        containerColor = MaterialTheme.colorScheme.errorContainer,
    )
}

@Composable
private fun StateLayout(
    title: String,
    message: String,
    icon: ImageVector,
    actionText: String?,
    onAction: (() -> Unit)?,
    contentColor: Color,
    modifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(containerColor, MaterialTheme.shapes.large)
                .padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(32.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = contentColor, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = contentColor, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            AppButton(
                text = actionText,
                onClick = onAction,
                style = AppButtonStyle.Outlined,
                modifier = actionModifier.padding(top = spacing.sm),
            )
        }
    }
}

@Preview
@Composable
private fun StateViewsLightPreview() = StateViewsPreview(darkTheme = false)

@Preview
@Composable
private fun StateViewsDarkPreview() = StateViewsPreview(darkTheme = true)

@Composable
private fun StateViewsPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EmptyState(
                title = stringResource(Res.string.preview_empty_title),
                message = stringResource(Res.string.preview_empty_message),
                actionText = stringResource(Res.string.preview_empty_action),
                onAction = {},
            )
            ErrorState(message = stringResource(Res.string.preview_error_message), onRetry = {})
        }
    }
}
