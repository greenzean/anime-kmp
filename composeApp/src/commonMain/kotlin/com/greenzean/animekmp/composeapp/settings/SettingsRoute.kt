package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavGraphBuilder.settingsGraph(onNavigateBack: () -> Unit) {
    composable<SettingsRoute> {
        SettingsRoot(onNavigateBack = onNavigateBack, modifier = Modifier.safeDrawingPadding())
    }
}
