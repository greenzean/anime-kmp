package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.greenzean.animekmp.composeapp.designsystem.components.AppCard
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_title
import com.greenzean.animekmp.composeapp.mal.MalConnectionContent
import com.greenzean.animekmp.shared.mal.presentation.MalAccountAction
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus
import com.greenzean.animekmp.shared.mal.presentation.MalImportAction
import com.greenzean.animekmp.shared.mal.presentation.MalImportState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Composable
fun MalAccountSection(
    state: MalAccountState,
    onAction: (MalAccountAction) -> Unit,
    modifier: Modifier = Modifier,
    importState: MalImportState = MalImportState(),
    onImportAction: (MalImportAction) -> Unit = {},
    initialNow: Instant = Clock.System.now(),
) {
    var now by remember { mutableStateOf(initialNow) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1.minutes)
            now = Clock.System.now()
        }
    }
    Column(modifier = modifier) {
        Text(
            stringResource(Res.string.settings_mal_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs).semantics { heading() },
        )
        AppCard(modifier = Modifier.padding(top = MaterialTheme.spacing.md)) {
            MalConnectionContent(state = state, onAction = onAction)
            if (state.status is MalAccountStatus.Connected) {
                MalImportRow(state = importState, onAction = onImportAction)
                MalSendRow(
                    pendingCount = state.pendingCount,
                    lastError = state.lastError,
                    onRetryClick = { onAction(MalAccountAction.OnRetryClick) },
                )
                MalPullRow(
                    lastSyncedAt = state.lastSyncedAt,
                    now = now,
                    syncing = state.syncing,
                    onSyncNowClick = { onAction(MalAccountAction.OnSyncNowClick) },
                )
            }
        }
    }
}
