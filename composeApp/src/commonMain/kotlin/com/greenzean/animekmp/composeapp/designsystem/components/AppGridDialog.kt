package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.greenzean.animekmp.composeapp.designsystem.MinTouchTarget
import com.greenzean.animekmp.composeapp.designsystem.spacing

@Composable
fun AppGridDialog(
    title: String,
    items: List<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(MinTouchTarget),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            ) {
                items(items, key = { it }) { number ->
                    GridCell(number = number, isSelected = number == selected, onClick = { onSelect(number) })
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun GridCell(
    number: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (isSelected) colors.primary else colors.surfaceContainerHighest
    val content = if (isSelected) colors.onPrimary else colors.onSurface
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
                .clip(MaterialTheme.shapes.medium)
                .background(background)
                .semantics { this.selected = isSelected }
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), style = MaterialTheme.typography.bodyLarge, color = content)
    }
}
