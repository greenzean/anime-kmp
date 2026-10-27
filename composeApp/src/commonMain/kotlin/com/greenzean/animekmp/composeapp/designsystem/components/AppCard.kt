package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.preview_card_body
import com.greenzean.animekmp.composeapp.generated.resources.settings_account_heading
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            content = content,
        )
    }
}

@Preview
@Composable
private fun AppCardLightPreview() = AppCardPreview(darkTheme = false)

@Preview
@Composable
private fun AppCardDarkPreview() = AppCardPreview(darkTheme = true)

@Composable
private fun AppCardPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        AppCard(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.settings_account_heading), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.preview_card_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
