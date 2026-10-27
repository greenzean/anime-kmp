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
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.for_you_coming_soon_message
import com.greenzean.animekmp.composeapp.generated.resources.for_you_coming_soon_title
import com.greenzean.animekmp.composeapp.generated.resources.for_you_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun ForYouScreen(modifier: Modifier = Modifier) {
    AppScaffold(title = stringResource(Res.string.for_you_title), modifier = modifier) { padding ->
        EmptyState(
            title = stringResource(Res.string.for_you_coming_soon_title),
            message = stringResource(Res.string.for_you_coming_soon_message),
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
