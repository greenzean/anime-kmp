package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.preview_row_pending
import com.greenzean.animekmp.composeapp.generated.resources.preview_row_supporting
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_plan_to_watch
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppListRow(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val spacing = MaterialTheme.spacing
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(clickable)
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Preview
@Composable
private fun AppListRowLightPreview() = AppListRowPreview(darkTheme = false)

@Preview
@Composable
private fun AppListRowDarkPreview() = AppListRowPreview(darkTheme = true)

@Composable
private fun AppListRowPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        Column {
            AppListRow(
                title = "Sousou no Frieren",
                supportingText =
                    stringResource(
                        Res.string.preview_row_supporting,
                    ),
                onClick = {
                },
            )
            AppListRow(
                title = "Kimi ni Todoke",
                supportingText = stringResource(Res.string.preview_row_pending),
                trailing = {
                    Text(
                        stringResource(Res.string.watch_status_plan_to_watch),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}
