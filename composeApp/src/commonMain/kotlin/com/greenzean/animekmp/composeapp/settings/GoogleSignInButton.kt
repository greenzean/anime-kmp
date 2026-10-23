package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.MinTouchTarget
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.google_g
import org.jetbrains.compose.resources.painterResource

private const val LABEL = "Sign in with Google"

// Google branding guidelines: https://developers.google.com/identity/branding-guidelines
// Fixed brand colors (light and dark variants), not part of the app color scheme.
private val LightFill = Color(0xFFFFFFFF)
private val LightStroke = Color(0xFF747775)
private val LightText = Color(0xFF1F1F1F)
private val DarkFill = Color(0xFF131314)
private val DarkStroke = Color(0xFF8E918F)
private val DarkText = Color(0xFFE3E3E3)

/**
 * "Sign in with Google" button following Google's branding: official "G" (aspect ratio kept),
 * fixed text, 12dp left of the logo, 10dp between logo and text, 12dp right of the text, 1dp stroke.
 * While [loading] it shows a spinner at the same size and ignores taps.
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val fill = if (darkTheme) DarkFill else LightFill
    val stroke = if (darkTheme) DarkStroke else LightStroke
    val text = if (darkTheme) DarkText else LightText
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier =
            modifier
                .defaultMinSize(minWidth = 220.dp, minHeight = MinTouchTarget)
                .semantics { contentDescription = if (loading) "$LABEL, in progress" else LABEL },
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = fill,
                contentColor = text,
                disabledContainerColor = fill,
                disabledContentColor = text,
            ),
        border = BorderStroke(1.dp, stroke),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = text)
            } else {
                // The "G" is 200x204 px; height 20dp with the ratio kept.
                Image(
                    painter = painterResource(Res.drawable.google_g),
                    contentDescription = null,
                    modifier = Modifier.height(20.dp),
                )
                Text(LABEL, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Preview
@Composable
private fun GoogleSignInButtonLightPreview() = GoogleSignInButtonPreview(darkTheme = false)

@Preview
@Composable
private fun GoogleSignInButtonDarkPreview() = GoogleSignInButtonPreview(darkTheme = true)

@Composable
private fun GoogleSignInButtonPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GoogleSignInButton(onClick = {}, darkTheme = darkTheme)
            GoogleSignInButton(onClick = {}, loading = true, darkTheme = darkTheme)
        }
    }
}
