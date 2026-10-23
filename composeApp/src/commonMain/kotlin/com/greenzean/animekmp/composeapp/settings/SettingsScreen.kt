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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.auth.domain.SignInError
import com.greenzean.animekmp.shared.auth.presentation.AccountAction
import com.greenzean.animekmp.shared.auth.presentation.AccountState
import com.greenzean.animekmp.shared.auth.presentation.AccountViewModel
import com.greenzean.animekmp.shared.core.domain.DataError
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoot(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onAction = viewModel::onAction, onNavigateBack = onNavigateBack, modifier = modifier)
}

@Composable
fun SettingsScreen(
    state: AccountState,
    onAction: (AccountAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    state.signOutPrompt?.let { prompt ->
        SignOutDialog(
            prompt = prompt,
            onKeep = { onAction(AccountAction.OnKeepLibraryClick) },
            onDelete = { onAction(AccountAction.OnDeleteLibraryClick) },
            onDismiss = { onAction(AccountAction.OnDismissSignOut) },
        )
    }
    AppScaffold(title = "Settings", onNavigateBack = onNavigateBack, modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            Text(
                "Account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs).semantics { heading() },
            )
            AppCard {
                if (state.isSignedIn) {
                    SignedInAccount(onSignOut = { onAction(AccountAction.OnSignOutClick) })
                } else {
                    SignedOutAccount(state = state, onAction = onAction)
                }
            }
            MergeStatus(
                merge = state.merge,
                onRetry = { onAction(AccountAction.OnRetryMerge) },
                onDismiss = { onAction(AccountAction.OnDismissMerge) },
            )
        }
    }
}

@Composable
private fun SignedInAccount(onSignOut: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
        AccountSummary()
        AppButton(
            text = "Sign out",
            onClick = onSignOut,
            style = AppButtonStyle.Outlined,
            modifier = Modifier.align(Alignment.End),
        )
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
            Text("Signed in with Google", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your library syncs to your account.",
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
            "An account is optional. Sign in to sync your library between your devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GoogleSignInButton(
            onClick = { onAction(AccountAction.OnSignInWithGoogleClick) },
            loading = state.isSigningIn,
        )
        state.error?.let { error ->
            ErrorBanner(message = error.message(), onDismiss = { onAction(AccountAction.OnDismissError) })
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    AppBanner(message = message) {
        AppButton(text = "Dismiss", onClick = onDismiss, style = AppButtonStyle.Text)
    }
}

private fun SignInError.message(): String =
    when (this) {
        is SignInError.Google -> {
            when (reason) {
                GoogleSignInError.NO_CREDENTIAL -> "No Google account was found on this device."
                GoogleSignInError.NOT_CONFIGURED -> "Sign-in is not set up in this build."
                GoogleSignInError.CANCELLED, GoogleSignInError.UNKNOWN -> "Google sign-in failed. Try again."
            }
        }

        is SignInError.Server -> {
            when (reason) {
                DataError.Network.NO_INTERNET, DataError.Network.REQUEST_TIMEOUT -> "No connection. Try again online."
                DataError.Network.UNAUTHORIZED -> "The server did not accept this sign-in. Try again."
                else -> "Could not reach the server. Try again later."
            }
        }
    }
