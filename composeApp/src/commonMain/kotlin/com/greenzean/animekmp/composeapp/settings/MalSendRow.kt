package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.greenzean.animekmp.composeapp.designsystem.MinTouchTarget
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_send_failed
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_send_pending
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_send_retry
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_send_up_to_date
import com.greenzean.animekmp.shared.mal.domain.MalSendError
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun MalSendRow(
    pendingCount: Int,
    lastError: MalSendError?,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failed = lastError != null
    val text =
        when {
            failed -> stringResource(Res.string.settings_mal_send_failed)
            pendingCount > 0 -> pluralStringResource(Res.plurals.settings_mal_send_pending, pendingCount, pendingCount)
            else -> stringResource(Res.string.settings_mal_send_up_to_date)
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MinTouchTarget)
                .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm)
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).testTag(MalSendTags.STATUS),
        )
        if (failed) {
            AppButton(
                text = stringResource(Res.string.settings_mal_send_retry),
                onClick = onRetryClick,
                style = AppButtonStyle.Text,
                modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget).testTag(MalSendTags.RETRY),
            )
        }
    }
}
