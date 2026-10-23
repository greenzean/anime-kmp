package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.greenzean.animekmp.composeapp.designsystem.components.AppBanner
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.components.AppCard
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.shared.auth.domain.MergeSummary
import com.greenzean.animekmp.shared.auth.presentation.MergeState
import com.greenzean.animekmp.shared.core.domain.DataError

@Composable
fun MergeStatus(
    merge: MergeState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (merge) {
        MergeState.Idle -> Unit
        MergeState.Merging -> MergingCard(modifier)
        is MergeState.Done -> MergeSummaryCard(merge.summary, onDismiss, modifier)
        is MergeState.Failed -> MergeFailedBanner(merge.error, onRetry, onDismiss, modifier)
    }
}

@Composable
private fun MergingCard(modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(MaterialTheme.spacing.xl))
            Column {
                Text("Merging your library", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Adding this device's anime to your account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MergeSummaryCard(
    summary: MergeSummary,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text("Library merged", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            if (summary.sent > 0) {
                SummaryLine("${summary.sent} ${summary.sent.entries()} from this device merged into your account.")
            }
            SummaryLine("Your library now has ${summary.total} anime.")
            if (summary.rejected > 0) {
                Text(
                    "${summary.rejected} ${summary.rejected.entries()} could not be added and stay on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        AppButton(
            text = "Done",
            onClick = onDismiss,
            style = AppButtonStyle.Tonal,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun SummaryLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MergeFailedBanner(
    error: DataError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canRetry = error != DataError.Network.UNAUTHORIZED
    AppBanner(message = error.mergeMessage(), modifier = modifier) {
        AppButton(text = "Dismiss", onClick = onDismiss, style = AppButtonStyle.Text)
        if (canRetry) AppButton(text = "Retry", onClick = onRetry, style = AppButtonStyle.Text)
    }
}

private fun DataError.mergeMessage(): String =
    when (this) {
        DataError.Network.UNAUTHORIZED -> {
            "Your session ended. Sign in again to merge your library."
        }

        DataError.Network.NO_INTERNET, DataError.Network.REQUEST_TIMEOUT -> {
            "You are signed in, but your library was not merged yet. It will finish when you are online."
        }

        else -> {
            "The server could not be reached. Your library will be merged later."
        }
    }

private fun Int.entries() = if (this == 1) "entry" else "entries"
