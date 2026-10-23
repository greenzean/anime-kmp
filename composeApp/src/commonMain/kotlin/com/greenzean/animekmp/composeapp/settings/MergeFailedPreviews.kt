package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.shared.auth.presentation.MergeState
import com.greenzean.animekmp.shared.core.domain.DataError

@Preview
@Composable
private fun MergeFailedLightPreview() = MergeFailedPreview(DataError.Network.NO_INTERNET, darkTheme = false)

@Preview
@Composable
private fun MergeFailedDarkPreview() = MergeFailedPreview(DataError.Network.NO_INTERNET, darkTheme = true)

@Preview
@Composable
private fun MergeSessionEndedLightPreview() = MergeFailedPreview(DataError.Network.UNAUTHORIZED, darkTheme = false)

@Preview
@Composable
private fun MergeSessionEndedDarkPreview() = MergeFailedPreview(DataError.Network.UNAUTHORIZED, darkTheme = true)

@Composable
private fun MergeFailedPreview(
    error: DataError,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) {
        MergeStatus(
            merge = MergeState.Failed(error),
            onRetry = {},
            onDismiss = {},
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
        )
    }
}
