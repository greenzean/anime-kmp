package com.greenzean.animekmp.composeapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.library.LibraryRoute
import com.greenzean.animekmp.composeapp.library.libraryGraph
import com.greenzean.animekmp.composeapp.settings.SettingsRoute
import com.greenzean.animekmp.composeapp.settings.settingsGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun App(
    modifier: Modifier = Modifier,
    deepLinks: Flow<String> = emptyFlow(),
) {
    AppTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            val navController = rememberNavController()
            LaunchedEffect(navController, deepLinks) {
                deepLinks.collect { uri ->
                    try {
                        navController.navigate(NavUri(uri))
                    } catch (_: IllegalArgumentException) {
                        Unit
                    }
                }
            }
            NavHost(navController = navController, startDestination = LibraryRoute) {
                libraryGraph(onNavigateToSettings = { navController.navigate(SettingsRoute) })
                settingsGraph(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
