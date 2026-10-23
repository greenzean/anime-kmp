package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.shared.auth.domain.MergeSummary
import com.greenzean.animekmp.shared.auth.presentation.MergeState

@Preview
@Composable
private fun MergeMergingLightPreview() = MergePreview(MergeState.Merging, darkTheme = false)

@Preview
@Composable
private fun MergeMergingDarkPreview() = MergePreview(MergeState.Merging, darkTheme = true)

@Preview
@Composable
private fun MergeDoneLightPreview() =
    MergePreview(MergeState.Done(MergeSummary(sent = 12, rejected = 0, total = 40)), darkTheme = false)

@Preview
@Composable
private fun MergeDoneDarkPreview() =
    MergePreview(MergeState.Done(MergeSummary(sent = 12, rejected = 0, total = 40)), darkTheme = true)

@Preview
@Composable
private fun MergeDoneRejectedLightPreview() =
    MergePreview(MergeState.Done(MergeSummary(sent = 12, rejected = 2, total = 40)), darkTheme = false)

@Preview
@Composable
private fun MergeDoneRejectedDarkPreview() =
    MergePreview(MergeState.Done(MergeSummary(sent = 12, rejected = 2, total = 40)), darkTheme = true)

@Preview
@Composable
private fun MergeDoneEmptyLightPreview() =
    MergePreview(MergeState.Done(MergeSummary(sent = 0, rejected = 0, total = 0)), darkTheme = false)

@Preview
@Composable
private fun MergeDoneEmptyDarkPreview() =
    MergePreview(MergeState.Done(MergeSummary(sent = 0, rejected = 0, total = 0)), darkTheme = true)

@Composable
private fun MergePreview(
    merge: MergeState,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) {
        MergeStatus(merge = merge, onRetry = {}, onDismiss = {}, modifier = Modifier.padding(MaterialTheme.spacing.lg))
    }
}
