package com.greenzean.animekmp.composeapp.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenzean.animekmp.composeapp.core.ObserveAsEvents
import com.greenzean.animekmp.composeapp.designsystem.AppScaffold
import com.greenzean.animekmp.composeapp.designsystem.components.AnimeCover
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.components.AppTagRow
import com.greenzean.animekmp.composeapp.designsystem.components.CoverSize
import com.greenzean.animekmp.composeapp.designsystem.components.EmptyState
import com.greenzean.animekmp.composeapp.designsystem.components.ErrorState
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.shared.details.presentation.AiringStatus
import com.greenzean.animekmp.shared.details.presentation.AnimeDetailsUi
import com.greenzean.animekmp.shared.details.presentation.DetailsAction
import com.greenzean.animekmp.shared.details.presentation.DetailsEvent
import com.greenzean.animekmp.shared.details.presentation.DetailsState
import com.greenzean.animekmp.shared.details.presentation.DetailsStatus
import com.greenzean.animekmp.shared.details.presentation.DetailsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DetailsRoot(
    malId: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = koinViewModel(key = "details-$malId") { parametersOf(malId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            DetailsEvent.NavigateBack -> onNavigateBack()
        }
    }
    DetailsScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun DetailsScreen(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        title = "Details",
        modifier = modifier,
        onNavigateBack = { onAction(DetailsAction.OnBackClick) },
    ) { padding ->
        val messagePadding = Modifier.padding(padding).padding(MaterialTheme.spacing.lg)
        when (state.status) {
            DetailsStatus.Loading -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = "Loading details" })
                }
            }

            DetailsStatus.Loaded -> {
                state.details?.let { details ->
                    DetailsContent(
                        details = details,
                        inLibrary = state.inLibrary,
                        onAction = onAction,
                        modifier = Modifier.padding(padding),
                    )
                }
            }

            DetailsStatus.Offline -> {
                EmptyState(
                    title = "You're offline",
                    message = "Details need a connection the first time. Your library still works.",
                    icon = AppIcons.Warning,
                    actionText = "Try again",
                    onAction = { onAction(DetailsAction.OnRetryClick) },
                    modifier = messagePadding,
                )
            }

            DetailsStatus.Error -> {
                ErrorState(
                    message = "MyAnimeList could not be reached. Try again.",
                    onRetry = { onAction(DetailsAction.OnRetryClick) },
                    modifier = messagePadding,
                )
            }
        }
    }
}

@Composable
private fun DetailsContent(
    details: AnimeDetailsUi,
    inLibrary: Boolean,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
            AnimeCover(details.imageUrl, size = CoverSize.Large)
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.weight(1f)) {
                Text(details.title, style = MaterialTheme.typography.titleLarge)
                details.otherTitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val facts = details.facts()
                if (facts.isNotEmpty()) {
                    Text(
                        facts,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (inLibrary) {
            Text("In library", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        } else {
            AppButton(
                text = "Add to library",
                onClick = { onAction(DetailsAction.OnAddClick) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (details.genres.isNotEmpty()) {
            AppTagRow(details.genres)
        }
        Text(
            details.synopsis ?: "No synopsis available.",
            style = MaterialTheme.typography.bodyLarge,
        )
        AppButton(
            text = "View on MyAnimeList",
            onClick = { uriHandler.openUri("https://myanimelist.net/anime/${details.malId}") },
            style = AppButtonStyle.Outlined,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun AnimeDetailsUi.facts(): String =
    listOfNotNull(
        airingStatus?.label(),
        episodes?.let { if (it == 1) "1 episode" else "$it episodes" },
        meanScore?.let { "MAL score $it" },
        startDate?.let { "${if (airingStatus == AiringStatus.NotYetAired) "Starts" else "Started"} $it" },
    ).joinToString(" · ")

private fun AiringStatus.label(): String =
    when (this) {
        AiringStatus.Finished -> "Finished airing"
        AiringStatus.Airing -> "Currently airing"
        AiringStatus.NotYetAired -> "Not yet aired"
    }
