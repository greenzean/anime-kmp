package com.greenzean.animekmp.composeapp.foryou

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.greenzean.animekmp.composeapp.core.ObserveAsEvents
import com.greenzean.animekmp.shared.mal.presentation.MalAccountViewModel
import com.greenzean.animekmp.shared.suggestions.presentation.SuggestionsEvent
import com.greenzean.animekmp.shared.suggestions.presentation.SuggestionsViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
data object ForYouRoute

fun NavGraphBuilder.forYouGraph(onNavigateToDetails: (Int) -> Unit) {
    composable<ForYouRoute> {
        ForYouRoot(onNavigateToDetails = onNavigateToDetails, modifier = Modifier.safeDrawingPadding())
    }
}

@Composable
fun ForYouRoot(
    onNavigateToDetails: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SuggestionsViewModel = koinViewModel(),
    malViewModel: MalAccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val malState by malViewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SuggestionsEvent.NavigateToDetails -> onNavigateToDetails(event.malId)
        }
    }
    ForYouScreen(
        state = state,
        malState = malState,
        onAction = viewModel::onAction,
        onMalAction = malViewModel::onAction,
        modifier = modifier,
    )
}
