package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val PREVIEW_NOW = Instant.fromEpochSeconds(1_800_000_000)
private val PREVIEW_MINUTES = 12.minutes
private val PREVIEW_HOURS = 5.hours
private val PREVIEW_DAYS = 3.days
private val PREVIEW_JUST_NOW = 10.seconds

@Preview
@Composable
private fun MalPullLightPreview() = MalPullPreview(darkTheme = false)

@Preview
@Composable
private fun MalPullDarkPreview() = MalPullPreview(darkTheme = true)

@Composable
private fun MalPullPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        Column {
            PreviewRow(null, false)
            PreviewRow(PREVIEW_JUST_NOW, false)
            PreviewRow(PREVIEW_MINUTES, false)
            PreviewRow(PREVIEW_HOURS, false)
            PreviewRow(PREVIEW_DAYS, false)
            PreviewRow(PREVIEW_MINUTES, true)
        }
    }
}

@Composable
private fun PreviewRow(
    ago: Duration?,
    syncing: Boolean,
) {
    MalPullRow(
        lastSyncedAt = ago?.let { PREVIEW_NOW - it },
        now = PREVIEW_NOW,
        syncing = syncing,
        onSyncNowClick = {},
    )
}
