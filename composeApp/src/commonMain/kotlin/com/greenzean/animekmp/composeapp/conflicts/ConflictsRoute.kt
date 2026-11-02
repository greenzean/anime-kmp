package com.greenzean.animekmp.composeapp.conflicts

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ConflictsRoute

fun NavGraphBuilder.conflictsGraph(onNavigateBack: () -> Unit) {
    composable<ConflictsRoute> {
        ConflictsRoot(onNavigateBack = onNavigateBack)
    }
}
