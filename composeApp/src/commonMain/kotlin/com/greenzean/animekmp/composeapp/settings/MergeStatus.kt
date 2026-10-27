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
import com.greenzean.animekmp.composeapp.core.UiText
import com.greenzean.animekmp.composeapp.designsystem.components.AppBanner
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.components.AppCard
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_dismiss
import com.greenzean.animekmp.composeapp.generated.resources.action_done
import com.greenzean.animekmp.composeapp.generated.resources.action_retry
import com.greenzean.animekmp.composeapp.generated.resources.merge_done_title
import com.greenzean.animekmp.composeapp.generated.resources.merge_error_offline
import com.greenzean.animekmp.composeapp.generated.resources.merge_error_server
import com.greenzean.animekmp.composeapp.generated.resources.merge_error_unauthorized
import com.greenzean.animekmp.composeapp.generated.resources.merge_merging_message
import com.greenzean.animekmp.composeapp.generated.resources.merge_merging_title
import com.greenzean.animekmp.composeapp.generated.resources.merge_rejected
import com.greenzean.animekmp.composeapp.generated.resources.merge_sent
import com.greenzean.animekmp.composeapp.generated.resources.merge_total
import com.greenzean.animekmp.shared.auth.domain.MergeSummary
import com.greenzean.animekmp.shared.auth.presentation.MergeState
import com.greenzean.animekmp.shared.core.domain.DataError
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
                Text(stringResource(Res.string.merge_merging_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.merge_merging_message),
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
        Text(stringResource(Res.string.merge_done_title), style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            if (summary.sent > 0) {
                SummaryLine(pluralStringResource(Res.plurals.merge_sent, summary.sent, summary.sent))
            }
            SummaryLine(stringResource(Res.string.merge_total, summary.total))
            if (summary.rejected > 0) {
                Text(
                    pluralStringResource(Res.plurals.merge_rejected, summary.rejected, summary.rejected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        AppButton(
            text = stringResource(Res.string.action_done),
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
    AppBanner(message = error.mergeMessage().asString(), modifier = modifier) {
        AppButton(text = stringResource(Res.string.action_dismiss), onClick = onDismiss, style = AppButtonStyle.Text)
        if (canRetry) {
            AppButton(
                text = stringResource(Res.string.action_retry),
                onClick = onRetry,
                style = AppButtonStyle.Text,
            )
        }
    }
}

private fun DataError.mergeMessage(): UiText =
    when (this) {
        DataError.Network.UNAUTHORIZED -> {
            UiText.StringResource(Res.string.merge_error_unauthorized)
        }

        DataError.Network.NO_INTERNET, DataError.Network.REQUEST_TIMEOUT -> {
            UiText.StringResource(Res.string.merge_error_offline)
        }

        else -> {
            UiText.StringResource(Res.string.merge_error_server)
        }
    }
