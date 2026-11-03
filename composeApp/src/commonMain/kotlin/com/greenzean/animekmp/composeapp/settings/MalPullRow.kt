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
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_last_synced_days
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_last_synced_hours
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_last_synced_just_now
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_last_synced_minutes
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_last_synced_never
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_sync_now
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_syncing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Composable
fun MalPullRow(
    lastSyncedAt: Instant?,
    now: Instant,
    syncing: Boolean,
    onSyncNowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            lastSyncedText(lastSyncedAt, now),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).testTag(MalPullTags.STATUS),
        )
        AppButton(
            text =
                stringResource(
                    if (syncing) Res.string.settings_mal_syncing else Res.string.settings_mal_sync_now,
                ),
            onClick = onSyncNowClick,
            style = AppButtonStyle.Text,
            enabled = !syncing,
            loading = syncing,
            modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget).testTag(MalPullTags.SYNC_NOW),
        )
    }
}

@Composable
private fun lastSyncedText(
    lastSyncedAt: Instant?,
    now: Instant,
): String {
    if (lastSyncedAt == null) return stringResource(Res.string.settings_mal_last_synced_never)
    val elapsed = now - lastSyncedAt
    return when {
        elapsed < 1.minutes -> {
            stringResource(Res.string.settings_mal_last_synced_just_now)
        }

        elapsed < 1.hours -> {
            val count = elapsed.inWholeMinutes.toInt()
            pluralStringResource(Res.plurals.settings_mal_last_synced_minutes, count, count)
        }

        elapsed < 1.days -> {
            val count = elapsed.inWholeHours.toInt()
            pluralStringResource(Res.plurals.settings_mal_last_synced_hours, count, count)
        }

        else -> {
            val count = elapsed.inWholeDays.toInt()
            pluralStringResource(Res.plurals.settings_mal_last_synced_days, count, count)
        }
    }
}
