package com.greenzean.animekmp.composeapp.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_dismiss
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_connect
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_connected_as
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_connected_no_name
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_connecting
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_consent_accept
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_consent_body
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_disconnect
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_error_cancelled
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_error_denied
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_error_no_browser
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_error_no_internet
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_error_other
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_title
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.mal.presentation.MalAccountAction
import com.greenzean.animekmp.shared.mal.presentation.MalAccountError
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
private class MalAccountRobot(
    private val test: ComposeUiTest,
    status: MalAccountStatus,
) {
    val actions = mutableListOf<MalAccountAction>()

    init {
        test.setContent {
            AppTheme {
                MalAccountSection(
                    state = MalAccountState(status),
                    onAction = { actions += it },
                )
            }
        }
    }

    private fun tagCount(tag: String) = test.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    fun assertTextShown(text: String) = apply { test.onNodeWithText(text).assertIsDisplayed() }

    fun assertTextMissing(text: String) =
        apply { assertEquals(0, test.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size) }

    fun assertHeading(text: String) =
        apply {
            test
                .onNode(hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                .assertExists()
        }

    fun assertTagShown(tag: String) = apply { test.onNodeWithTag(tag).assertIsDisplayed() }

    fun assertTagMissing(tag: String) = apply { assertEquals(0, tagCount(tag)) }

    fun assertStatusText(text: String) =
        apply { test.onNodeWithTag(MalAccountTags.STATUS).assertTextContains(text, substring = true) }

    fun assertErrorText(text: String) = apply { test.onNode(hasText(text, substring = true)).assertIsDisplayed() }

    fun assertTagNotClickable(tag: String) =
        apply {
            if (tagCount(tag) > 0) test.onNodeWithTag(tag).assertIsNotEnabled()
        }

    fun assertTouchTarget(tag: String) = apply { test.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp) }

    fun assertLabelledButton(text: String) =
        apply {
            test.onNode(hasText(text) and hasClickAction()).assertIsDisplayed()
        }

    fun clickTag(tag: String) = apply { test.onNodeWithTag(tag).performClick() }

    fun clickButton(text: String) = apply { test.onNode(hasText(text) and hasClickAction()).performClick() }

    fun assertActions(vararg expected: MalAccountAction) = apply { assertEquals(expected.toList(), actions) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class MalAccountSectionTest {
    @Test
    fun needsConsentShowsTitleBodyAndAcceptOnly() =
        runComposeUiTest {
            MalAccountRobot(this, MalAccountStatus.NeedsConsent)
                .assertHeading(str(Res.string.settings_mal_title))
                .assertTextShown(str(Res.string.settings_mal_consent_body))
                .assertTagShown(MalAccountTags.CONSENT_ACCEPT)
                .assertLabelledButton(str(Res.string.settings_mal_consent_accept))
                .assertTagMissing(MalAccountTags.CONNECT)
                .assertTagMissing(MalAccountTags.DISCONNECT)
                .assertTagMissing(MalAccountTags.ERROR)
        }

    @Test
    fun acceptingConsentSendsConsentAccepted() =
        runComposeUiTest {
            MalAccountRobot(this, MalAccountStatus.NeedsConsent)
                .assertTouchTarget(MalAccountTags.CONSENT_ACCEPT)
                .clickTag(MalAccountTags.CONSENT_ACCEPT)
                .assertActions(MalAccountAction.OnConsentAccepted)
        }

    @Test
    fun notConnectedShowsConnectButton() =
        runComposeUiTest {
            MalAccountRobot(this, MalAccountStatus.NotConnected)
                .assertHeading(str(Res.string.settings_mal_title))
                .assertTagShown(MalAccountTags.CONNECT)
                .assertLabelledButton(str(Res.string.settings_mal_connect))
                .assertTagMissing(MalAccountTags.CONSENT_ACCEPT)
                .assertTagMissing(MalAccountTags.DISCONNECT)
                .assertTextMissing(str(Res.string.settings_mal_consent_body))
        }

    @Test
    fun connectButtonSendsConnectClick() =
        runComposeUiTest {
            MalAccountRobot(this, MalAccountStatus.NotConnected)
                .assertTouchTarget(MalAccountTags.CONNECT)
                .clickTag(MalAccountTags.CONNECT)
                .assertActions(MalAccountAction.OnConnectClick)
        }

    @Test
    fun connectingShowsProgressAndTheConnectButtonIsNotUsable() =
        runComposeUiTest {
            MalAccountRobot(this, MalAccountStatus.Connecting)
                .assertTagShown(MalAccountTags.STATUS)
                .assertStatusText(str(Res.string.settings_mal_connecting))
                .assertTagNotClickable(MalAccountTags.CONNECT)
                .assertTagMissing(MalAccountTags.DISCONNECT)
                .assertTagMissing(MalAccountTags.CONSENT_ACCEPT)
        }

    @Test
    fun connectedShowsUsernameAndDisconnect() =
        runComposeUiTest {
            MalAccountRobot(this, MalAccountStatus.Connected("francisco"))
                .assertTagShown(MalAccountTags.STATUS)
                .assertStatusText(str(Res.string.settings_mal_connected_as, "francisco"))
                .assertTagShown(MalAccountTags.DISCONNECT)
                .assertLabelledButton(str(Res.string.settings_mal_disconnect))
                .assertTagMissing(MalAccountTags.CONNECT)
                .assertTagMissing(MalAccountTags.CONSENT_ACCEPT)
        }

    @Test
    fun connectedWithoutUsernameShowsTheFallbackText() =
        runComposeUiTest {
            MalAccountRobot(this, MalAccountStatus.Connected(null))
                .assertStatusText(str(Res.string.settings_mal_connected_no_name))
                .assertTagShown(MalAccountTags.DISCONNECT)
        }

    @Test
    fun disconnectButtonSendsDisconnectClick() =
        runComposeUiTest {
            MalAccountRobot(this, MalAccountStatus.Connected("francisco"))
                .assertTouchTarget(MalAccountTags.DISCONNECT)
                .clickTag(MalAccountTags.DISCONNECT)
                .assertActions(MalAccountAction.OnDisconnectClick)
        }

    @Test
    fun cancelledErrorMessage() = assertError(MalAccountError.Cancelled, str(Res.string.settings_mal_error_cancelled))

    @Test
    fun accessDeniedErrorMessage() =
        assertError(MalAccountError.AccessDenied, str(Res.string.settings_mal_error_denied))

    @Test
    fun noBrowserErrorMessage() = assertError(MalAccountError.NoBrowser, str(Res.string.settings_mal_error_no_browser))

    @Test
    fun noInternetErrorMessage() =
        assertError(MalAccountError.NoInternet, str(Res.string.settings_mal_error_no_internet))

    @Test
    fun otherErrorMessage() = assertError(MalAccountError.Other, str(Res.string.settings_mal_error_other))

    @Test
    fun errorOffersDismiss() =
        runComposeUiTest {
            MalAccountRobot(this, MalAccountStatus.Error(MalAccountError.Cancelled))
                .assertTagShown(MalAccountTags.ERROR)
                .assertLabelledButton(str(Res.string.action_dismiss))
                .clickButton(str(Res.string.action_dismiss))
                .assertActions(MalAccountAction.OnErrorDismissed)
        }

    @Test
    fun errorRetrySendsConnectClick() =
        runComposeUiTest {
            MalAccountRobot(this, MalAccountStatus.Error(MalAccountError.NoInternet))
                .assertTouchTarget(MalAccountTags.RETRY)
                .clickTag(MalAccountTags.RETRY)
                .assertActions(MalAccountAction.OnConnectClick)
                .assertTagMissing(MalAccountTags.CONNECT)
        }

    @Test
    fun noErrorOutsideTheErrorStatus() =
        runComposeUiTest {
            MalAccountRobot(this, MalAccountStatus.NotConnected)
                .assertTagMissing(MalAccountTags.ERROR)
                .assertTextMissing(str(Res.string.settings_mal_error_other))
        }

    private fun assertError(
        reason: MalAccountError,
        message: String,
    ) = runComposeUiTest {
        MalAccountRobot(this, MalAccountStatus.Error(reason))
            .assertTagShown(MalAccountTags.ERROR)
            .assertErrorText(message)
    }
}
