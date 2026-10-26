package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object DeleteAccountRoute

private const val DELETE_ACCOUNT_URI = "animekmp://delete-account"

fun NavGraphBuilder.settingsGraph(onNavigateBack: () -> Unit) {
    composable<SettingsRoute> {
        SettingsRoot(onNavigateBack = onNavigateBack, modifier = Modifier.safeDrawingPadding())
    }
    composable<DeleteAccountRoute>(deepLinks = listOf(navDeepLink<DeleteAccountRoute>(basePath = DELETE_ACCOUNT_URI))) {
        SettingsRoot(
            onNavigateBack = onNavigateBack,
            modifier = Modifier.safeDrawingPadding(),
            openDeleteAccount = true,
        )
    }
}
