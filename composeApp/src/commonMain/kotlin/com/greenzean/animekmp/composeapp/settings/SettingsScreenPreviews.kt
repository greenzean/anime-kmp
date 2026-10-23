package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.auth.domain.SignInError
import com.greenzean.animekmp.shared.auth.presentation.AccountState

@Preview
@Composable
private fun SettingsSignedOutLightPreview() = SettingsPreview(AccountState(), darkTheme = false)

@Preview
@Composable
private fun SettingsSignedOutDarkPreview() = SettingsPreview(AccountState(), darkTheme = true)

@Preview
@Composable
private fun SettingsSigningInLightPreview() = SettingsPreview(AccountState(isSigningIn = true), darkTheme = false)

@Preview
@Composable
private fun SettingsSigningInDarkPreview() = SettingsPreview(AccountState(isSigningIn = true), darkTheme = true)

@Preview
@Composable
private fun SettingsErrorLightPreview() = SettingsPreview(errorState(), darkTheme = false)

@Preview
@Composable
private fun SettingsErrorDarkPreview() = SettingsPreview(errorState(), darkTheme = true)

@Preview
@Composable
private fun SettingsSignedInLightPreview() = SettingsPreview(AccountState(isSignedIn = true), darkTheme = false)

@Preview
@Composable
private fun SettingsSignedInDarkPreview() = SettingsPreview(AccountState(isSignedIn = true), darkTheme = true)

private fun errorState() = AccountState(error = SignInError.Google(GoogleSignInError.NO_CREDENTIAL))

@Composable
private fun SettingsPreview(
    state: AccountState,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) { SettingsScreen(state = state, onAction = {}, onNavigateBack = {}) }
}
