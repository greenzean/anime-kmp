package com.greenzean.animekmp.composeapp.details

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data class DetailsRoute(
    val malId: Int,
)

fun NavGraphBuilder.detailsGraph(onNavigateBack: () -> Unit) {
    composable<DetailsRoute> { entry ->
        DetailsRoot(
            malId = entry.toRoute<DetailsRoute>().malId,
            onNavigateBack = onNavigateBack,
            modifier = Modifier.safeDrawingPadding(),
        )
    }
}
