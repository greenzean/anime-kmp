package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_score_badge
import com.greenzean.animekmp.composeapp.generated.resources.entry_score_none
import org.jetbrains.compose.resources.stringResource

private const val SINGLE_DIGIT_MAX = 9

@Composable
fun AppScoreStar(
    score: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val colors = MaterialTheme.colorScheme
    val description =
        if (score == null) {
            stringResource(Res.string.entry_score_none)
        } else {
            stringResource(Res.string.cd_score_badge, score)
        }
    Box(
        modifier = modifier.size(size).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (score == null) AppIcons.StarOutline else AppIcons.Star,
            contentDescription = null,
            modifier = Modifier.size(size),
            tint = if (score == null) colors.outline else colors.primary,
        )
        if (score != null) {
            val style =
                if (score >
                    SINGLE_DIGIT_MAX
                ) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelMedium
                }
            Text(
                text = score.toString(),
                style = style,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimary,
            )
        }
    }
}

@Composable
private fun ScoreStarPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            AppScoreStar(score = null)
            AppScoreStar(score = 7)
            AppScoreStar(score = 10)
        }
    }
}

@Preview
@Composable
private fun AppScoreStarLightPreview() = ScoreStarPreview(darkTheme = false)

@Preview
@Composable
private fun AppScoreStarDarkPreview() = ScoreStarPreview(darkTheme = true)
