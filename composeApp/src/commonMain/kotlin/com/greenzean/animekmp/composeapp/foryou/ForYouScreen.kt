package com.greenzean.animekmp.composeapp.foryou

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.AppScaffold
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.components.EmptyState
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing

@Composable
fun ForYouScreen(modifier: Modifier = Modifier) {
    AppScaffold(title = "For you", modifier = modifier) { padding ->
        EmptyState(
            title = "Coming soon",
            message = "Recommendations based on your library will show up here.",
            icon = AppIcons.Star,
            modifier = Modifier.padding(padding).padding(MaterialTheme.spacing.lg),
        )
    }
}

@Preview
@Composable
private fun ForYouLightPreview() = ThemedPreview(darkTheme = false) { ForYouScreen() }

@Preview
@Composable
private fun ForYouDarkPreview() = ThemedPreview(darkTheme = true) { ForYouScreen() }
