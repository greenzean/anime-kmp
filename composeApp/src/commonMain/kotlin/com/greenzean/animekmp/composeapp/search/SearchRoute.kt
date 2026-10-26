package com.greenzean.animekmp.composeapp.search

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute

fun NavGraphBuilder.searchGraph() {
    composable<SearchRoute> {
        SearchRoot(modifier = Modifier.safeDrawingPadding())
    }
}
