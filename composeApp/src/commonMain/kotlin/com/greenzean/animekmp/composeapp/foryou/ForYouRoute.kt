package com.greenzean.animekmp.composeapp.foryou

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ForYouRoute

fun NavGraphBuilder.forYouGraph() {
    composable<ForYouRoute> {
        ForYouScreen(modifier = Modifier.safeDrawingPadding())
    }
}
