package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.MinTouchTarget
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing

@Composable
fun <T> AppSortMenu(
    label: String,
    description: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    optionModifier: (T) -> Modifier = { Modifier },
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            modifier =
                buttonModifier
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .semantics { contentDescription = description },
        ) {
            Icon(AppIcons.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, modifier = Modifier.padding(start = MaterialTheme.spacing.sm))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = { RadioButton(selected = isSelected, onClick = null) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier =
                        optionModifier(option).semantics {
                            role = Role.RadioButton
                            this.selected = isSelected
                        },
                )
            }
        }
    }
}
