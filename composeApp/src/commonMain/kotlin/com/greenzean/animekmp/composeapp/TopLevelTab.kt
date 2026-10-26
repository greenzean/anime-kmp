package com.greenzean.animekmp.composeapp

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.foryou.ForYouRoute
import com.greenzean.animekmp.composeapp.library.LibraryRoute
import com.greenzean.animekmp.composeapp.search.SearchRoute

enum class TopLevelTab(
    val route: Any,
    val matches: (NavDestination) -> Boolean,
    val label: String,
    val icon: ImageVector,
) {
    Library(LibraryRoute, { it.hasRoute<LibraryRoute>() }, "Library", AppIcons.List),
    Search(SearchRoute, { it.hasRoute<SearchRoute>() }, "Search", AppIcons.Search),
    ForYou(ForYouRoute, { it.hasRoute<ForYouRoute>() }, "For you", AppIcons.Star),
}
