package com.greenzean.animekmp.composeapp

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.foryou.ForYouRoute
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.tab_for_you
import com.greenzean.animekmp.composeapp.generated.resources.tab_library
import com.greenzean.animekmp.composeapp.generated.resources.tab_search
import com.greenzean.animekmp.composeapp.library.LibraryRoute
import com.greenzean.animekmp.composeapp.search.SearchRoute
import org.jetbrains.compose.resources.StringResource

enum class TopLevelTab(
    val route: Any,
    val matches: (NavDestination) -> Boolean,
    val label: StringResource,
    val icon: ImageVector,
) {
    Library(LibraryRoute, { it.hasRoute<LibraryRoute>() }, Res.string.tab_library, AppIcons.List),
    Search(SearchRoute, { it.hasRoute<SearchRoute>() }, Res.string.tab_search, AppIcons.Search),
    ForYou(ForYouRoute, { it.hasRoute<ForYouRoute>() }, Res.string.tab_for_you, AppIcons.Star),
}
