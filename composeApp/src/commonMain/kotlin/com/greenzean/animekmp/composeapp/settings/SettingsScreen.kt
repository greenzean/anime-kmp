package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.auth.domain.SignInError
import com.greenzean.animekmp.shared.auth.presentation.AccountAction
import com.greenzean.animekmp.shared.auth.presentation.AccountState
import com.greenzean.animekmp.shared.auth.presentation.AccountViewModel
import com.greenzean.animekmp.shared.core.domain.DataError
import org.jetbrains.compose.ui.tooling.preview.Preview
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
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onNavigateBack) { Text("Back") }
        }
        Text("Account", style = MaterialTheme.typography.titleMedium)
        if (state.isSignedIn) {
            Text("Signed in with Google. Your library syncs to your account.")
        } else {
            SignedOutAccount(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun SignedOutAccount(
    state: AccountState,
    onAction: (AccountAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("An account is optional. Sign in to sync your library between your devices.")
        Button(
            onClick = { onAction(AccountAction.OnSignInWithGoogleClick) },
            enabled = !state.isSigningIn,
            modifier = Modifier.semantics { contentDescription = "Sign in with Google" },
        ) {
            if (state.isSigningIn) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Sign in with Google")
            }
        }
        state.error?.let { error ->
            Row(
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(error.message(), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAction(AccountAction.OnDismissError) }) { Text("Dismiss") }
            }
        }
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

@Preview
@Composable
private fun SettingsScreenSignedOutPreview() {
    AppTheme {
        Surface { SettingsScreen(state = AccountState(), onAction = {}, onNavigateBack = {}) }
    }
}

@Preview
@Composable
private fun SettingsScreenErrorPreview() {
    AppTheme {
        Surface {
            SettingsScreen(
                state = AccountState(error = SignInError.Google(GoogleSignInError.NO_CREDENTIAL)),
                onAction = {},
                onNavigateBack = {},
            )
        }
    }
}

@Preview
@Composable
private fun SettingsScreenSignedInPreview() {
    AppTheme {
        Surface { SettingsScreen(state = AccountState(isSignedIn = true), onAction = {}, onNavigateBack = {}) }
    }
}
