package com.greenzean.animekmp.composeapp

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppBottomBar(
    selected: TopLevelTab?,
    onSelect: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        TopLevelTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

@Preview
@Composable
private fun AppBottomBarLightPreview() = ThemedPreview(darkTheme = false) { AppBottomBar(TopLevelTab.Search, {}) }

@Preview
@Composable
private fun AppBottomBarDarkPreview() = ThemedPreview(darkTheme = true) { AppBottomBar(TopLevelTab.Search, {}) }
