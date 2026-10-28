package com.greenzean.animekmp.composeapp.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_cancel
import com.greenzean.animekmp.composeapp.generated.resources.action_dismiss
import com.greenzean.animekmp.composeapp.generated.resources.action_done
import com.greenzean.animekmp.composeapp.generated.resources.action_retry
import com.greenzean.animekmp.composeapp.generated.resources.action_try_again
import com.greenzean.animekmp.composeapp.generated.resources.cd_back
import com.greenzean.animekmp.composeapp.generated.resources.cd_in_progress
import com.greenzean.animekmp.composeapp.generated.resources.cd_settings
import com.greenzean.animekmp.composeapp.generated.resources.delete_account_confirm_with_google
import com.greenzean.animekmp.composeapp.generated.resources.delete_account_kept
import com.greenzean.animekmp.composeapp.generated.resources.delete_account_permanent
import com.greenzean.animekmp.composeapp.generated.resources.delete_error_unauthorized
import com.greenzean.animekmp.composeapp.generated.resources.display_english
import com.greenzean.animekmp.composeapp.generated.resources.display_english_supporting
import com.greenzean.animekmp.composeapp.generated.resources.display_heading
import com.greenzean.animekmp.composeapp.generated.resources.display_main_title
import com.greenzean.animekmp.composeapp.generated.resources.display_title_language
import com.greenzean.animekmp.composeapp.generated.resources.mal_connect
import com.greenzean.animekmp.composeapp.generated.resources.mal_consent_body
import com.greenzean.animekmp.composeapp.generated.resources.merge_done_title
import com.greenzean.animekmp.composeapp.generated.resources.merge_error_offline
import com.greenzean.animekmp.composeapp.generated.resources.merge_error_server
import com.greenzean.animekmp.composeapp.generated.resources.merge_error_unauthorized
import com.greenzean.animekmp.composeapp.generated.resources.merge_merging_message
import com.greenzean.animekmp.composeapp.generated.resources.merge_merging_title
import com.greenzean.animekmp.composeapp.generated.resources.merge_rejected
import com.greenzean.animekmp.composeapp.generated.resources.merge_sent
import com.greenzean.animekmp.composeapp.generated.resources.merge_total
import com.greenzean.animekmp.composeapp.generated.resources.settings_account_heading
import com.greenzean.animekmp.composeapp.generated.resources.settings_delete_account
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_title
import com.greenzean.animekmp.composeapp.generated.resources.settings_sign_out
import com.greenzean.animekmp.composeapp.generated.resources.settings_signed_in_message
import com.greenzean.animekmp.composeapp.generated.resources.settings_signed_in_title
import com.greenzean.animekmp.composeapp.generated.resources.settings_signed_out_message
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_error_google_failed
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_error_no_connection
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_error_no_credential
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_error_not_configured
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_error_server
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_error_unauthorized
import com.greenzean.animekmp.composeapp.generated.resources.sign_in_with_google
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_delete
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_keep
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_preparing
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_ready
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_ready_with_unsynced
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_signing_out
import com.greenzean.animekmp.composeapp.generated.resources.sign_out_unsynced
import com.greenzean.animekmp.composeapp.mal.MalAccountTags
import com.greenzean.animekmp.composeapp.plural
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.auth.domain.MergeSummary
import com.greenzean.animekmp.shared.auth.domain.SignInError
import com.greenzean.animekmp.shared.auth.presentation.AccountAction
import com.greenzean.animekmp.shared.auth.presentation.AccountState
import com.greenzean.animekmp.shared.auth.presentation.DeletePrompt
import com.greenzean.animekmp.shared.auth.presentation.MergeState
import com.greenzean.animekmp.shared.auth.presentation.SignOutPrompt
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.mal.presentation.MalAccountAction
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import com.greenzean.animekmp.shared.settings.presentation.DisplaySettingsAction
import com.greenzean.animekmp.shared.settings.presentation.DisplaySettingsState
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

private val signedIn = AccountState(isSignedIn = true, sessionChecked = true)
private val signedOut = AccountState(sessionChecked = true)

@OptIn(ExperimentalTestApi::class)
private class SettingsRobot(
    private val test: ComposeUiTest,
    state: AccountState,
    displayState: DisplaySettingsState = DisplaySettingsState(),
    malState: MalAccountState = MalAccountState(),
) {
    val malActions = mutableListOf<MalAccountAction>()
    val actions = mutableListOf<AccountAction>()
    val displayActions = mutableListOf<DisplaySettingsAction>()
    var backClicks = 0

    init {
        test.setContent {
            AppTheme {
                SettingsScreen(
                    state = state,
                    onAction = { actions += it },
                    onNavigateBack = { backClicks++ },
                    displayState = displayState,
                    onDisplayAction = { displayActions += it },
                    malState = malState,
                    onMalAction = { malActions += it },
                )
            }
        }
    }

    private fun button(text: String) = test.onNode(hasText(text) and hasClickAction())

    fun assertTextShown(text: String) = apply { test.onNodeWithText(text).assertIsDisplayed() }

    fun assertTextMissing(text: String) =
        apply { assertEquals(0, test.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size) }

    fun assertHeading(text: String) =
        apply {
            test
                .onNode(
                    hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
                ).assertExists()
        }

    fun assertLabelShown(label: String) = apply { test.onNodeWithContentDescription(label).assertIsDisplayed() }

    fun assertLabelMissing(label: String) =
        apply { assertEquals(0, test.onAllNodes(hasContentDescription(label)).fetchSemanticsNodes().size) }

    fun clickButton(text: String) = apply { button(text).performClick() }

    fun clickLabel(label: String) = apply { test.onNodeWithContentDescription(label).performClick() }

    fun assertButtonEnabled(text: String) = apply { button(text).assertIsEnabled() }

    fun assertButtonDisabled(text: String) = apply { button(text).assertIsNotEnabled() }

    fun assertButtonMissing(text: String) =
        apply { assertEquals(0, test.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().size) }

    fun assertTouchHeight(text: String) = apply { button(text).assertHeightIsAtLeast(48.dp) }

    fun assertBackTouchTarget() =
        apply {
            test
                .onNodeWithContentDescription(str(Res.string.cd_back))
                .assertTouchWidthIsEqualTo(48.dp)
                .assertTouchHeightIsEqualTo(48.dp)
        }

    fun assertRadioSelected(label: String) = apply { button(label).assertIsSelected() }

    fun assertRadioNotSelected(label: String) = apply { button(label).assertIsNotSelected() }

    fun assertActions(vararg expected: AccountAction) = apply { assertEquals(expected.toList(), actions) }

    fun assertDisplayActions(vararg expected: DisplaySettingsAction) =
        apply {
            assertEquals(expected.toList(), displayActions)
        }

    fun assertTagShown(tag: String) = apply { test.onNodeWithTag(tag).assertIsDisplayed() }

    fun clickTag(tag: String) = apply { test.onNodeWithTag(tag).performClick() }

    fun assertMalActions(vararg expected: MalAccountAction) = apply { assertEquals(expected.toList(), malActions) }

    fun assertBackClicks(expected: Int) = apply { assertEquals(expected, backClicks) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class SettingsScreenTest {
    @Test
    fun signedOutShowsOptionalAccountAndGoogleButton() =
        runComposeUiTest {
            SettingsRobot(this, signedOut)
                .assertHeading(str(Res.string.cd_settings))
                .assertHeading(str(Res.string.settings_account_heading))
                .assertTextShown(str(Res.string.settings_signed_out_message))
                .assertLabelShown(str(Res.string.sign_in_with_google))
                .assertButtonMissing(str(Res.string.settings_sign_out))
                .assertButtonMissing(str(Res.string.settings_delete_account))
        }

    @Test
    fun signedInShowsSummaryWithSignOutAndDeleteAccount() =
        runComposeUiTest {
            SettingsRobot(this, signedIn)
                .assertTextShown(str(Res.string.settings_signed_in_title))
                .assertTextShown(str(Res.string.settings_signed_in_message))
                .assertButtonEnabled(str(Res.string.settings_sign_out))
                .assertButtonEnabled(str(Res.string.settings_delete_account))
                .assertLabelMissing(str(Res.string.sign_in_with_google))
        }

    @Test
    fun googleButtonSendsSignInClick() =
        runComposeUiTest {
            SettingsRobot(this, signedOut)
                .clickLabel(str(Res.string.sign_in_with_google))
                .assertActions(AccountAction.OnSignInWithGoogleClick)
        }

    @Test
    fun signingInShowsProgressLabelAndIgnoresTaps() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(isSigningIn = true))
                .assertLabelShown(str(Res.string.cd_in_progress, str(Res.string.sign_in_with_google)))
                .clickLabel(str(Res.string.cd_in_progress, str(Res.string.sign_in_with_google)))
                .assertActions()
        }

    @Test
    fun signOutButtonSendsSignOutClick() =
        runComposeUiTest {
            SettingsRobot(
                this,
                signedIn,
            ).clickButton(str(Res.string.settings_sign_out)).assertActions(AccountAction.OnSignOutClick)
        }

    @Test
    fun deleteAccountButtonSendsDeleteAccountClick() =
        runComposeUiTest {
            SettingsRobot(
                this,
                signedIn,
            ).clickButton(str(Res.string.settings_delete_account)).assertActions(AccountAction.OnDeleteAccountClick)
        }

    @Test
    fun backButtonSendsNavigateBack() =
        runComposeUiTest {
            SettingsRobot(this, signedOut).clickLabel(str(Res.string.cd_back)).assertBackClicks(1)
        }

    @Test
    fun noGoogleAccountErrorMessage() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(error = SignInError.Google(GoogleSignInError.NO_CREDENTIAL)))
                .assertTextShown(str(Res.string.sign_in_error_no_credential))
        }

    @Test
    fun notConfiguredErrorMessage() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(error = SignInError.Google(GoogleSignInError.NOT_CONFIGURED)))
                .assertTextShown(str(Res.string.sign_in_error_not_configured))
        }

    @Test
    fun offlineSignInErrorMessage() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(error = SignInError.Server(DataError.Network.NO_INTERNET)))
                .assertTextShown(str(Res.string.sign_in_error_no_connection))
        }

    @Test
    fun rejectedSignInErrorMessage() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(error = SignInError.Server(DataError.Network.UNAUTHORIZED)))
                .assertTextShown(str(Res.string.sign_in_error_unauthorized))
        }

    @Test
    fun serverSignInErrorMessage() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(error = SignInError.Server(DataError.Network.SERVER_ERROR)))
                .assertTextShown(str(Res.string.sign_in_error_server))
        }

    @Test
    fun errorBannerDismissSendsDismissError() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(error = SignInError.Google(GoogleSignInError.UNKNOWN)))
                .assertTextShown(str(Res.string.sign_in_error_google_failed))
                .assertTouchHeight(str(Res.string.action_dismiss))
                .clickButton(str(Res.string.action_dismiss))
                .assertActions(AccountAction.OnDismissError)
        }

    @Test
    fun noErrorBannerWithoutError() =
        runComposeUiTest {
            SettingsRobot(this, signedOut).assertButtonMissing(str(Res.string.action_dismiss))
        }

    @Test
    fun mergingShowsProgressCard() =
        runComposeUiTest {
            SettingsRobot(this, signedIn.copy(merge = MergeState.Merging))
                .assertTextShown(str(Res.string.merge_merging_title))
                .assertTextShown(str(Res.string.merge_merging_message))
        }

    @Test
    fun mergeDoneShowsSummaryAndDoneDismisses() =
        runComposeUiTest {
            SettingsRobot(
                this,
                signedIn.copy(merge = MergeState.Done(MergeSummary(sent = 3, rejected = 0, total = 10))),
            ).assertTextShown(str(Res.string.merge_done_title))
                .assertTextShown(plural(Res.plurals.merge_sent, 3, 3))
                .assertTextShown(str(Res.string.merge_total, 10))
                .assertTextMissing(plural(Res.plurals.merge_rejected, 2, 2).substringAfter(" "))
                .assertTouchHeight(str(Res.string.action_done))
                .clickButton(str(Res.string.action_done))
                .assertActions(AccountAction.OnDismissMerge)
        }

    @Test
    fun mergeDoneUsesSingularForOneEntryAndShowsRejected() =
        runComposeUiTest {
            SettingsRobot(this, signedIn.copy(merge = MergeState.Done(MergeSummary(sent = 1, rejected = 1, total = 4))))
                .assertTextShown(plural(Res.plurals.merge_sent, 1, 1))
                .assertTextShown(plural(Res.plurals.merge_rejected, 1, 1))
        }

    @Test
    fun mergeDoneHidesSentLineWhenNothingWasSent() =
        runComposeUiTest {
            SettingsRobot(this, signedIn.copy(merge = MergeState.Done(MergeSummary(sent = 0, rejected = 2, total = 5))))
                .assertTextMissing(plural(Res.plurals.merge_sent, 2, 2).substringAfter(" "))
                .assertTextShown(plural(Res.plurals.merge_rejected, 2, 2))
        }

    @Test
    fun mergeFailedOfflineOffersRetryAndDismiss() =
        runComposeUiTest {
            SettingsRobot(this, signedIn.copy(merge = MergeState.Failed(DataError.Network.NO_INTERNET)))
                .assertTextShown(
                    str(Res.string.merge_error_offline),
                ).assertTouchHeight(str(Res.string.action_retry))
                .clickButton(str(Res.string.action_retry))
                .clickButton(str(Res.string.action_dismiss))
                .assertActions(AccountAction.OnRetryMerge, AccountAction.OnDismissMerge)
        }

    @Test
    fun mergeFailedServerShowsServerMessageWithRetry() =
        runComposeUiTest {
            SettingsRobot(this, signedIn.copy(merge = MergeState.Failed(DataError.Network.SERVER_ERROR)))
                .assertTextShown(str(Res.string.merge_error_server))
                .assertButtonEnabled(str(Res.string.action_retry))
        }

    @Test
    fun mergeFailedUnauthorizedHidesRetry() =
        runComposeUiTest {
            SettingsRobot(this, signedIn.copy(merge = MergeState.Failed(DataError.Network.UNAUTHORIZED)))
                .assertTextShown(str(Res.string.merge_error_unauthorized))
                .assertButtonMissing(str(Res.string.action_retry))
                .assertButtonEnabled(str(Res.string.action_dismiss))
        }

    @Test
    fun noMergeStatusWhenIdle() =
        runComposeUiTest {
            SettingsRobot(this, signedIn)
                .assertTextMissing(str(Res.string.merge_merging_title))
                .assertTextMissing(str(Res.string.merge_done_title))
                .assertButtonMissing(str(Res.string.action_retry))
        }

    @Test
    fun signOutPreparingDisablesKeepAndDelete() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(signOutPrompt = SignOutPrompt.Preparing))
                .assertTextShown(str(Res.string.sign_out_preparing))
                .assertButtonDisabled(str(Res.string.sign_out_keep))
                .assertButtonDisabled(str(Res.string.sign_out_delete))
                .assertButtonDisabled(str(Res.string.action_cancel))
        }

    @Test
    fun signingOutDisablesKeepAndDelete() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(signOutPrompt = SignOutPrompt.SigningOut))
                .assertTextShown(str(Res.string.sign_out_signing_out))
                .assertButtonDisabled(str(Res.string.sign_out_keep))
                .assertButtonDisabled(str(Res.string.sign_out_delete))
        }

    @Test
    fun signOutReadyWithoutUnsyncedChangesShowsPlainMessage() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(signOutPrompt = SignOutPrompt.Ready(unsyncedCount = 0)))
                .assertTextShown(
                    str(Res.string.sign_out_ready),
                ).assertButtonEnabled(str(Res.string.sign_out_keep))
                .assertButtonEnabled(str(Res.string.sign_out_delete))
        }

    @Test
    fun signOutReadyWarnsAboutOneUnsyncedChange() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(signOutPrompt = SignOutPrompt.Ready(unsyncedCount = 1)))
                .assertTextShown(
                    str(
                        Res.string.sign_out_ready_with_unsynced,
                        str(Res.string.sign_out_ready),
                        plural(Res.plurals.sign_out_unsynced, 1, 1),
                    ),
                )
        }

    @Test
    fun signOutReadyPluralisesUnsyncedChanges() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(signOutPrompt = SignOutPrompt.Ready(unsyncedCount = 4)))
                .assertTextShown(
                    str(
                        Res.string.sign_out_ready_with_unsynced,
                        str(Res.string.sign_out_ready),
                        plural(Res.plurals.sign_out_unsynced, 4, 4),
                    ),
                )
        }

    @Test
    fun signOutDialogButtonsSendTheirActions() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(signOutPrompt = SignOutPrompt.Ready(unsyncedCount = 0)))
                .assertTouchHeight(str(Res.string.sign_out_keep))
                .assertTouchHeight(str(Res.string.sign_out_delete))
                .assertTouchHeight(str(Res.string.action_cancel))
                .clickButton(str(Res.string.sign_out_keep))
                .clickButton(str(Res.string.sign_out_delete))
                .clickButton(str(Res.string.action_cancel))
                .assertActions(
                    AccountAction.OnKeepLibraryClick,
                    AccountAction.OnDeleteLibraryClick,
                    AccountAction.OnDismissSignOut,
                )
        }

    @Test
    fun noSignOutDialogWithoutPrompt() =
        runComposeUiTest {
            SettingsRobot(this, signedIn).assertButtonMissing(str(Res.string.sign_out_keep))
        }

    @Test
    fun deleteDialogConfirmingExplainsConsequencesAndSendsActions() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(deletePrompt = DeletePrompt.Confirming))
                .assertHeading(str(Res.string.settings_delete_account))
                .assertTextShown(str(Res.string.delete_account_permanent))
                .assertTextShown(
                    str(Res.string.delete_account_kept),
                ).assertTextShown(str(Res.string.delete_account_confirm_with_google))
                .assertTouchHeight(str(Res.string.settings_delete_account))
                .assertTouchHeight(str(Res.string.action_cancel))
                .clickButton(str(Res.string.settings_delete_account))
                .clickButton(str(Res.string.action_cancel))
                .assertActions(AccountAction.OnConfirmDeleteAccountClick, AccountAction.OnDismissDeleteAccount)
        }

    @Test
    fun deleteDialogDeletingShowsProgressAndDisablesCancel() =
        runComposeUiTest {
            SettingsRobot(this, signedOut.copy(deletePrompt = DeletePrompt.Deleting))
                .assertLabelShown(str(Res.string.cd_in_progress, str(Res.string.settings_delete_account)))
                .assertButtonDisabled(str(Res.string.action_cancel))
                .clickLabel(str(Res.string.cd_in_progress, str(Res.string.settings_delete_account)))
                .assertActions()
        }

    @Test
    fun deleteDialogFailedShowsErrorAndTryAgain() =
        runComposeUiTest {
            SettingsRobot(
                this,
                signedOut.copy(deletePrompt = DeletePrompt.Failed(SignInError.Server(DataError.Network.UNAUTHORIZED))),
            ).assertTextShown(str(Res.string.delete_error_unauthorized))
                .assertButtonMissing(str(Res.string.settings_delete_account))
                .clickButton(str(Res.string.action_try_again))
                .assertActions(AccountAction.OnConfirmDeleteAccountClick)
        }

    @Test
    fun deleteDialogFailedOfflineShowsConnectionMessage() =
        runComposeUiTest {
            SettingsRobot(
                this,
                signedOut.copy(deletePrompt = DeletePrompt.Failed(SignInError.Server(DataError.Network.NO_INTERNET))),
            ).assertTextShown(str(Res.string.sign_in_error_no_connection))
        }

    @Test
    fun noDeleteDialogWithoutPrompt() =
        runComposeUiTest {
            SettingsRobot(this, signedIn).assertTextMissing(str(Res.string.delete_account_permanent))
        }

    @Test
    fun displaySectionShowsTitleLanguageOptions() =
        runComposeUiTest {
            SettingsRobot(this, signedOut)
                .assertHeading(str(Res.string.display_heading))
                .assertTextShown(str(Res.string.display_title_language))
                .assertTextShown(str(Res.string.display_english))
                .assertTextShown(str(Res.string.display_english_supporting))
                .assertTextShown(str(Res.string.display_main_title))
        }

    @Test
    fun englishIsSelectedWhenTitleLanguageIsEnglish() =
        runComposeUiTest {
            SettingsRobot(this, signedOut, DisplaySettingsState(TitleLanguage.ENGLISH))
                .assertRadioSelected(str(Res.string.display_english))
                .assertRadioNotSelected(str(Res.string.display_main_title))
        }

    @Test
    fun mainTitleIsSelectedWhenTitleLanguageIsMain() =
        runComposeUiTest {
            SettingsRobot(this, signedOut, DisplaySettingsState(TitleLanguage.MAIN))
                .assertRadioSelected(str(Res.string.display_main_title))
                .assertRadioNotSelected(str(Res.string.display_english))
        }

    @Test
    fun choosingAnOptionSendsTitleLanguageChange() =
        runComposeUiTest {
            SettingsRobot(this, signedOut, DisplaySettingsState(TitleLanguage.ENGLISH))
                .clickButton(str(Res.string.display_main_title))
                .clickButton(str(Res.string.display_english))
                .assertDisplayActions(
                    DisplaySettingsAction.OnTitleLanguageChange(TitleLanguage.MAIN),
                    DisplaySettingsAction.OnTitleLanguageChange(TitleLanguage.ENGLISH),
                )
        }

    @Test
    fun displayRowsMeetTheMinimumTouchTarget() =
        runComposeUiTest {
            SettingsRobot(this, signedOut)
                .assertTouchHeight(str(Res.string.display_english))
                .assertTouchHeight(str(Res.string.display_main_title))
        }

    @Test
    fun accountButtonsAndBackMeetTheMinimumTouchTarget() =
        runComposeUiTest {
            SettingsRobot(this, signedIn)
                .assertTouchHeight(str(Res.string.settings_sign_out))
                .assertTouchHeight(str(Res.string.settings_delete_account))
                .assertBackTouchTarget()
        }

    @Test
    fun malSectionShowsItsHeadingAndTheConnectFlow() =
        runComposeUiTest {
            SettingsRobot(this, signedOut, malState = MalAccountState(MalAccountStatus.NotConnected))
                .assertHeading(str(Res.string.settings_mal_title))
                .assertTagShown(MalAccountTags.CONNECT)
                .assertButtonEnabled(str(Res.string.mal_connect))
        }

    @Test
    fun malSectionAsksForConsentFirst() =
        runComposeUiTest {
            SettingsRobot(this, signedOut, malState = MalAccountState(MalAccountStatus.NeedsConsent))
                .assertTextShown(str(Res.string.mal_consent_body))
                .assertTagShown(MalAccountTags.CONSENT_ACCEPT)
                .clickTag(MalAccountTags.CONSENT_ACCEPT)
                .assertMalActions(MalAccountAction.OnConsentAccepted)
        }

    @Test
    fun malConnectSendsConnectClick() =
        runComposeUiTest {
            SettingsRobot(this, signedOut, malState = MalAccountState(MalAccountStatus.NotConnected))
                .clickTag(MalAccountTags.CONNECT)
                .assertMalActions(MalAccountAction.OnConnectClick)
        }

    @Test
    fun malSectionShowsDisconnectWhenConnected() =
        runComposeUiTest {
            SettingsRobot(this, signedOut, malState = MalAccountState(MalAccountStatus.Connected("francisco")))
                .assertTagShown(MalAccountTags.DISCONNECT)
                .clickTag(MalAccountTags.DISCONNECT)
                .assertMalActions(MalAccountAction.OnDisconnectClick)
        }
}
