package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_score_star
import org.jetbrains.compose.resources.stringResource

private const val STARS_PER_ROW = 5
private val StarCellMinSize = 56.dp
private val StarIconSize = 40.dp

@Composable
fun AppStarRatingDialog(
    title: String,
    selected: Int?,
    max: Int,
    onSelect: (Int) -> Unit,
    clearText: String,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.lg),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = MaterialTheme.spacing.lg,
                        vertical = MaterialTheme.spacing.xl,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Column {
                    for (row in (1..max).chunked(STARS_PER_ROW)) {
                        StarRow(row = row, selected = selected ?: 0, onSelect = onSelect)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AppButton(text = clearText, onClick = onClear, style = AppButtonStyle.Text)
                }
            }
        }
    }
}

@Composable
private fun StarRow(
    row: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        row.forEach { number ->
            Star(
                number = number,
                filled = number <= selected,
                onClick = { onSelect(number) },
                modifier = Modifier.weight(1f),
            )
        }
        repeat(STARS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun Star(
    number: Int,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.cd_score_star, number)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .defaultMinSize(minWidth = StarCellMinSize, minHeight = StarCellMinSize)
                .semantics {
                    this.selected = filled
                    contentDescription = description
                }.clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (filled) AppIcons.Star else AppIcons.StarOutline,
            contentDescription = null,
            modifier = Modifier.size(StarIconSize),
            tint = if (filled) colors.primary else colors.outline,
        )
    }
}

@Composable
private fun StarRatingPreview(
    darkTheme: Boolean,
    selected: Int?,
) {
    ThemedPreview(darkTheme) {
        AppStarRatingDialog(
            title = "Score",
            selected = selected,
            max = 10,
            onSelect = {},
            clearText = "No score",
            onClear = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun AppStarRatingDialogNoScorePreview() = StarRatingPreview(darkTheme = false, selected = null)

@Preview
@Composable
private fun AppStarRatingDialogTopRowPreview() = StarRatingPreview(darkTheme = false, selected = 3)

@Preview
@Composable
private fun AppStarRatingDialogBottomRowPreview() = StarRatingPreview(darkTheme = false, selected = 8)

@Preview
@Composable
private fun AppStarRatingDialogNoScoreDarkPreview() = StarRatingPreview(darkTheme = true, selected = null)

@Preview
@Composable
private fun AppStarRatingDialogTopRowDarkPreview() = StarRatingPreview(darkTheme = true, selected = 3)

@Preview
@Composable
private fun AppStarRatingDialogBottomRowDarkPreview() = StarRatingPreview(darkTheme = true, selected = 8)
