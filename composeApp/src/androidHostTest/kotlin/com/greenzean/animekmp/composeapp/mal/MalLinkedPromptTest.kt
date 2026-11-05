package com.greenzean.animekmp.composeapp.mal

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.mal_connect
import com.greenzean.animekmp.composeapp.generated.resources.mal_linked_connect
import com.greenzean.animekmp.composeapp.generated.resources.mal_linked_not_here
import com.greenzean.animekmp.composeapp.generated.resources.mal_linked_prompt
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.mal.presentation.MalAccountAction
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
private class LinkedPromptRobot(
    private val test: ComposeUiTest,
    state: MalAccountState,
) {
    val actions = mutableListOf<MalAccountAction>()

    init {
        test.setContent { AppTheme { MalConnectionContent(state = state, onAction = { actions += it }) } }
    }

    fun assertPromptShown() =
        apply {
            test.onNodeWithTag(MalAccountTags.LINKED_PROMPT).assertIsDisplayed()
            test.onNodeWithText(str(Res.string.mal_linked_prompt)).assertIsDisplayed()
            assertEquals(0, test.onAllNodesWithText(str(Res.string.mal_linked_connect)).fetchSemanticsNodes().size)
            test.onNodeWithText(str(Res.string.mal_linked_not_here)).assertIsDisplayed()
        }

    fun assertPromptMissing() =
        apply {
            listOf(MalAccountTags.LINKED_PROMPT, MalAccountTags.LINKED_NOT_HERE)
                .forEach { assertEquals(0, test.onAllNodesWithTag(it).fetchSemanticsNodes().size) }
        }

    fun assertTouchTargets() =
        apply {
            test.onNodeWithTag(MalAccountTags.LINKED_NOT_HERE).assertHeightIsAtLeast(48.dp)
        }

    fun assertSingleConnectButton() =
        apply {
            assertEquals(1, test.onAllNodesWithTag(MalAccountTags.CONNECT).fetchSemanticsNodes().size)
            assertEquals(1, test.onAllNodesWithText(str(Res.string.mal_connect)).fetchSemanticsNodes().size)
        }

    fun clickTag(tag: String) = apply { test.onNodeWithTag(tag).performClick() }

    fun assertActions(vararg expected: MalAccountAction) = apply { assertEquals(expected.toList(), actions) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class MalLinkedPromptTest {
    @Test
    fun linkedElsewhereShowsThePromptWithOnlyNotOnThisDevice() =
        runComposeUiTest {
            LinkedPromptRobot(this, MalAccountState(MalAccountStatus.NotConnected, linkedElsewhere = true))
                .assertPromptShown()
                .assertTouchTargets()
        }

    @Test
    fun withoutConsentThePromptOffersOnlyNotOnThisDevice() =
        runComposeUiTest {
            LinkedPromptRobot(this, MalAccountState(MalAccountStatus.NeedsConsent, linkedElsewhere = true))
            onNodeWithTag(MalAccountTags.LINKED_PROMPT).assertIsDisplayed()
            onNodeWithTag(MalAccountTags.LINKED_NOT_HERE).assertIsDisplayed()
            assertEquals(0, onAllNodesWithText(str(Res.string.mal_linked_connect)).fetchSemanticsNodes().size)
            assertEquals(0, onAllNodesWithTag(MalAccountTags.CONNECT).fetchSemanticsNodes().size)
        }

    @Test
    fun notLinkedElsewhereHidesThePrompt() =
        runComposeUiTest {
            LinkedPromptRobot(this, MalAccountState(MalAccountStatus.NotConnected, linkedElsewhere = false))
                .assertPromptMissing()
        }

    @Test
    fun connectedHidesThePrompt() =
        runComposeUiTest {
            LinkedPromptRobot(
                this,
                MalAccountState(MalAccountStatus.Connected("francisco"), linkedToAccount = true),
            ).assertPromptMissing()
        }

    @Test
    fun notConnectedLinkedElsewhereHasExactlyOneConnectButtonThatSendsConnectClick() =
        runComposeUiTest {
            LinkedPromptRobot(this, MalAccountState(MalAccountStatus.NotConnected, linkedElsewhere = true))
                .assertSingleConnectButton()
                .clickTag(MalAccountTags.CONNECT)
                .assertActions(MalAccountAction.OnConnectClick)
        }

    @Test
    fun notOnThisDeviceSendsItsAction() =
        runComposeUiTest {
            LinkedPromptRobot(this, MalAccountState(MalAccountStatus.NotConnected, linkedElsewhere = true))
                .clickTag(MalAccountTags.LINKED_NOT_HERE)
                .assertActions(MalAccountAction.OnNotOnThisDeviceClick)
        }
}
