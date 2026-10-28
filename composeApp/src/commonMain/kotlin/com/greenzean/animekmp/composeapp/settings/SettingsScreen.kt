package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenzean.animekmp.composeapp.designsystem.AppScaffold
import com.greenzean.animekmp.composeapp.designsystem.components.AppBanner
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.components.AppCard
import com.greenzean.animekmp.composeapp.designsystem.icons.AppIcons
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_dismiss
import com.greenzean.animekmp.composeapp.generated.resources.settings_account_heading
import com.greenzean.animekmp.composeapp.generated.resources.settings_delete_account
import com.greenzean.animekmp.composeapp.generated.resources.settings_sign_out
import com.greenzean.animekmp.composeapp.generated.resources.settings_signed_in_message
import com.greenzean.animekmp.composeapp.generated.resources.settings_signed_in_title
import com.greenzean.animekmp.composeapp.generated.resources.settings_signed_out_message
import com.greenzean.animekmp.composeapp.generated.resources.settings_title
import com.greenzean.animekmp.shared.auth.presentation.AccountAction
import com.greenzean.animekmp.shared.auth.presentation.AccountState
import com.greenzean.animekmp.shared.auth.presentation.AccountViewModel
import com.greenzean.animekmp.shared.mal.presentation.MalAccountAction
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountViewModel
import com.greenzean.animekmp.shared.settings.presentation.DisplaySettingsAction
import com.greenzean.animekmp.shared.settings.presentation.DisplaySettingsState
import com.greenzean.animekmp.shared.settings.presentation.DisplaySettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoot(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    openDeleteAccount: Boolean = false,
    viewModel: AccountViewModel = koinViewModel(),
    displayViewModel: DisplaySettingsViewModel = koinViewModel(),
    malViewModel: MalAccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val displayState by displayViewModel.state.collectAsStateWithLifecycle()
    val malState by malViewModel.state.collectAsStateWithLifecycle()
    var pendingDeleteAccount by rememberSaveable { mutableStateOf(openDeleteAccount) }
    LaunchedEffect(state.sessionChecked, state.isSignedIn, pendingDeleteAccount) {
        if (pendingDeleteAccount && state.sessionChecked) {
            pendingDeleteAccount = false
            if (state.isSignedIn) viewModel.onAction(AccountAction.OnDeleteAccountClick)
        }
    }
    SettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        displayState = displayState,
        onDisplayAction = displayViewModel::onAction,
        malState = malState,
        onMalAction = malViewModel::onAction,
    )
}

@Composable
fun SettingsScreen(
    state: AccountState,
    onAction: (AccountAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    displayState: DisplaySettingsState = DisplaySettingsState(),
    onDisplayAction: (DisplaySettingsAction) -> Unit = {},
    malState: MalAccountState = MalAccountState(),
    onMalAction: (MalAccountAction) -> Unit = {},
) {
    state.signOutPrompt?.let { prompt ->
        SignOutDialog(
            prompt = prompt,
            onKeep = { onAction(AccountAction.OnKeepLibraryClick) },
            onDelete = { onAction(AccountAction.OnDeleteLibraryClick) },
            onDismiss = { onAction(AccountAction.OnDismissSignOut) },
        )
    }
    state.deletePrompt?.let { prompt ->
        DeleteAccountDialog(
            prompt = prompt,
            onConfirm = { onAction(AccountAction.OnConfirmDeleteAccountClick) },
            onDismiss = { onAction(AccountAction.OnDismissDeleteAccount) },
        )
    }
    AppScaffold(
        title = stringResource(Res.string.settings_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            Text(
                stringResource(Res.string.settings_account_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs).semantics { heading() },
            )
            AppCard {
                if (state.isSignedIn) {
                    SignedInAccount(
                        onSignOut = { onAction(AccountAction.OnSignOutClick) },
                        onDeleteAccount = { onAction(AccountAction.OnDeleteAccountClick) },
                    )
                } else {
                    SignedOutAccount(state = state, onAction = onAction)
                }
            }
            MergeStatus(
                merge = state.merge,
                onRetry = { onAction(AccountAction.OnRetryMerge) },
                onDismiss = { onAction(AccountAction.OnDismissMerge) },
            )
            MalAccountSection(state = malState, onAction = onMalAction)
            DisplaySection(state = displayState, onAction = onDisplayAction)
        }
    }
}

@Composable
private fun SignedInAccount(
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
        AccountSummary()
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppButton(
                text = stringResource(Res.string.settings_sign_out),
                onClick = onSignOut,
                style = AppButtonStyle.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton(
                text = stringResource(Res.string.settings_delete_account),
                onClick = onDeleteAccount,
                style = AppButtonStyle.Destructive,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AccountSummary() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column {
            Text(stringResource(Res.string.settings_signed_in_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.settings_signed_in_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SignedOutAccount(
    state: AccountState,
    onAction: (AccountAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
        Text(
            stringResource(Res.string.settings_signed_out_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GoogleSignInButton(
            onClick = { onAction(AccountAction.OnSignInWithGoogleClick) },
            loading = state.isSigningIn,
        )
        state.error?.let { error ->
            ErrorBanner(message = error.toUiText().asString(), onDismiss = { onAction(AccountAction.OnDismissError) })
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    AppBanner(message = message) {
        AppButton(text = stringResource(Res.string.action_dismiss), onClick = onDismiss, style = AppButtonStyle.Text)
    }
}
