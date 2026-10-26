package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.MinTouchTarget
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.spacing

@Composable
fun AppRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MinTouchTarget)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun AppRadioRowLightPreview() = AppRadioRowPreview(darkTheme = false)

@Preview
@Composable
private fun AppRadioRowDarkPreview() = AppRadioRowPreview(darkTheme = true)

@Composable
private fun AppRadioRowPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        Column {
            AppRadioRow(
                label = "English",
                selected = true,
                onSelect = {},
                supportingText = "Falls back to the main title",
            )
            AppRadioRow(label = "Main title", selected = false, onSelect = {})
        }
    }
}
