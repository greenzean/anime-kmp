package com.greenzean.animekmp.composeapp.library

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

fun NavGraphBuilder.libraryGraph(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetails: (Int) -> Unit,
) {
    composable<LibraryRoute> {
        LibraryRoot(
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToDetails = onNavigateToDetails,
            modifier = Modifier.safeDrawingPadding(),
        )
    }
}
