package com.greenzean.animekmp.composeapp

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.foryou.forYouGraph
import com.greenzean.animekmp.composeapp.library.LibraryRoute
import com.greenzean.animekmp.composeapp.library.libraryGraph
import com.greenzean.animekmp.composeapp.search.searchGraph
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
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentTab =
                TopLevelTab.entries.firstOrNull { tab ->
                    backStackEntry?.destination?.hierarchy?.any { tab.matches(it) } == true
                }
            Scaffold(
                bottomBar = {
                    if (currentTab != null) {
                        AppBottomBar(selected = currentTab, onSelect = { navController.navigateToTab(it) })
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = LibraryRoute,
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                ) {
                    libraryGraph(onNavigateToSettings = { navController.navigate(SettingsRoute) })
                    searchGraph()
                    forYouGraph()
                    settingsGraph(onNavigateBack = { navController.popBackStack() })
                }
            }
        }
    }
}

private fun NavController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.route) {
        popUpTo<LibraryRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
