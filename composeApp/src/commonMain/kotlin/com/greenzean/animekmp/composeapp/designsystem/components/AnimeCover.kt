package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons

@Composable
fun AnimeCover(
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val frame = modifier.size(width = CoverWidth, height = CoverHeight).clip(MaterialTheme.shapes.small)
    if (imageUrl == null) {
        CoverPlaceholder(frame)
    } else {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = frame,
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() },
        )
    }
}

@Composable
private fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.List, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val CoverWidth = 48.dp
private val CoverHeight = 68.dp

@Preview
@Composable
private fun AnimeCoverLightPreview() = AnimeCoverPreview(darkTheme = false)

@Preview
@Composable
private fun AnimeCoverDarkPreview() = AnimeCoverPreview(darkTheme = true)

@Composable
private fun AnimeCoverPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) { AnimeCover(imageUrl = null, modifier = Modifier.size(width = 48.dp, height = 68.dp)) }
}
