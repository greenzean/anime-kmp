package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.mal.domain.MalSendError

private const val PREVIEW_PENDING = 3

@Preview
@Composable
private fun MalSendUpToDateLightPreview() = MalSendPreview(0, null, false)

@Preview
@Composable
private fun MalSendUpToDateDarkPreview() = MalSendPreview(0, null, true)

@Preview
@Composable
private fun MalSendPendingLightPreview() = MalSendPreview(PREVIEW_PENDING, null, false)

@Preview
@Composable
private fun MalSendPendingDarkPreview() = MalSendPreview(PREVIEW_PENDING, null, true)

@Preview
@Composable
private fun MalSendFailedLightPreview() = MalSendPreview(PREVIEW_PENDING, MalSendError.NO_CONNECTION, false)

@Preview
@Composable
private fun MalSendFailedDarkPreview() = MalSendPreview(PREVIEW_PENDING, MalSendError.NO_CONNECTION, true)

@Composable
private fun MalSendPreview(
    pendingCount: Int,
    lastError: MalSendError?,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) {
        Column { MalSendRow(pendingCount = pendingCount, lastError = lastError, onRetryClick = {}) }
    }
}
