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
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_add_to_library
import com.greenzean.animekmp.composeapp.generated.resources.action_try_again
import com.greenzean.animekmp.composeapp.generated.resources.airing_airing
import com.greenzean.animekmp.composeapp.generated.resources.airing_finished
import com.greenzean.animekmp.composeapp.generated.resources.airing_not_yet_aired
import com.greenzean.animekmp.composeapp.generated.resources.cd_loading_details
import com.greenzean.animekmp.composeapp.generated.resources.date_full
import com.greenzean.animekmp.composeapp.generated.resources.date_month_year
import com.greenzean.animekmp.composeapp.generated.resources.date_year
import com.greenzean.animekmp.composeapp.generated.resources.details_facts_separator
import com.greenzean.animekmp.composeapp.generated.resources.details_mal_score
import com.greenzean.animekmp.composeapp.generated.resources.details_no_synopsis
import com.greenzean.animekmp.composeapp.generated.resources.details_not_found_message
import com.greenzean.animekmp.composeapp.generated.resources.details_not_found_title
import com.greenzean.animekmp.composeapp.generated.resources.details_offline_message
import com.greenzean.animekmp.composeapp.generated.resources.details_started
import com.greenzean.animekmp.composeapp.generated.resources.details_starts
import com.greenzean.animekmp.composeapp.generated.resources.details_throttled_message
import com.greenzean.animekmp.composeapp.generated.resources.details_throttled_title
import com.greenzean.animekmp.composeapp.generated.resources.details_title
import com.greenzean.animekmp.composeapp.generated.resources.details_view_on_mal
import com.greenzean.animekmp.composeapp.generated.resources.episodes_count
import com.greenzean.animekmp.composeapp.generated.resources.error_mal_unreachable
import com.greenzean.animekmp.composeapp.generated.resources.label_in_library
import com.greenzean.animekmp.composeapp.generated.resources.month_names
import com.greenzean.animekmp.composeapp.generated.resources.offline_title
import com.greenzean.animekmp.shared.details.presentation.AiringStatus
import com.greenzean.animekmp.shared.details.presentation.AnimeDetailsUi
import com.greenzean.animekmp.shared.details.presentation.DetailsAction
import com.greenzean.animekmp.shared.details.presentation.DetailsEvent
import com.greenzean.animekmp.shared.details.presentation.DetailsState
import com.greenzean.animekmp.shared.details.presentation.DetailsStatus
import com.greenzean.animekmp.shared.details.presentation.DetailsViewModel
import com.greenzean.animekmp.shared.details.presentation.MalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
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
        title = stringResource(Res.string.details_title),
        modifier = modifier,
        onNavigateBack = { onAction(DetailsAction.OnBackClick) },
    ) { padding ->
        val loadingDescription = stringResource(Res.string.cd_loading_details)
        val messagePadding = Modifier.padding(padding).padding(MaterialTheme.spacing.lg)
        when (state.status) {
            DetailsStatus.Loading -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDescription })
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
                    title = stringResource(Res.string.offline_title),
                    message = stringResource(Res.string.details_offline_message),
                    icon = AppIcons.Warning,
                    actionText = stringResource(Res.string.action_try_again),
                    onAction = { onAction(DetailsAction.OnRetryClick) },
                    modifier = messagePadding,
                )
            }

            DetailsStatus.NotFound -> {
                EmptyState(
                    title = stringResource(Res.string.details_not_found_title),
                    message = stringResource(Res.string.details_not_found_message),
                    icon = AppIcons.Warning,
                    modifier = messagePadding,
                )
            }

            DetailsStatus.Throttled -> {
                ErrorState(
                    title = stringResource(Res.string.details_throttled_title),
                    message = stringResource(Res.string.details_throttled_message),
                    onRetry = { onAction(DetailsAction.OnRetryClick) },
                    modifier = messagePadding,
                )
            }

            DetailsStatus.Error -> {
                ErrorState(
                    message = stringResource(Res.string.error_mal_unreachable),
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
            Text(
                stringResource(Res.string.label_in_library),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            AppButton(
                text = stringResource(Res.string.action_add_to_library),
                onClick = { onAction(DetailsAction.OnAddClick) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (details.genres.isNotEmpty()) {
            AppTagRow(details.genres)
        }
        Text(
            details.synopsis ?: stringResource(Res.string.details_no_synopsis),
            style = MaterialTheme.typography.bodyLarge,
        )
        AppButton(
            text = stringResource(Res.string.details_view_on_mal),
            onClick = { uriHandler.openUri("https://myanimelist.net/anime/${details.malId}") },
            style = AppButtonStyle.Outlined,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AnimeDetailsUi.facts(): String {
    val startDateText =
        startDate?.let {
            val label =
                if (airingStatus ==
                    AiringStatus.NotYetAired
                ) {
                    Res.string.details_starts
                } else {
                    Res.string.details_started
                }
            stringResource(label, it.format())
        }
    return listOfNotNull(
        airingStatus?.let { stringResource(it.label()) },
        episodes?.let { pluralStringResource(Res.plurals.episodes_count, it, it) },
        meanScore?.let { stringResource(Res.string.details_mal_score, it.toString()) },
        startDateText,
    ).joinToString(stringResource(Res.string.details_facts_separator))
}

@Composable
internal fun MalDate.format(): String {
    val monthName = month?.let { stringArrayResource(Res.array.month_names)[it - 1] }
    val dayOfMonth = day
    return when {
        monthName == null -> stringResource(Res.string.date_year, year)
        dayOfMonth == null -> stringResource(Res.string.date_month_year, monthName, year)
        else -> stringResource(Res.string.date_full, monthName, dayOfMonth, year)
    }
}

private fun AiringStatus.label(): StringResource =
    when (this) {
        AiringStatus.Finished -> Res.string.airing_finished
        AiringStatus.Airing -> Res.string.airing_airing
        AiringStatus.NotYetAired -> Res.string.airing_not_yet_aired
    }
