package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.mal_unlink_failed
import com.greenzean.animekmp.composeapp.mal.MalAccountTags
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
private class SectionRobot(
    private val test: ComposeUiTest,
) {
    val actions = mutableListOf<MalAccountAction>()

    private var shown: MutableState<MalAccountState>? = null

    fun show(state: MalAccountState) =
        apply {
            val current = shown
            if (current == null) {
                val holder = mutableStateOf(state)
                shown = holder
                test.setContent {
                    AppTheme { MalAccountSection(state = holder.value, onAction = { actions += it }) }
                }
            } else {
                current.value = state
                test.waitForIdle()
            }
        }

    private fun count(tag: String) = test.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    fun assertUnlinkRowShown() = apply { assertEquals(1, count(MalAccountTags.UNLINK)) }

    fun assertUnlinkRowHidden() = apply { assertEquals(0, count(MalAccountTags.UNLINK)) }

    fun assertErrorShown() =
        apply {
            test.onNodeWithTag(MalAccountTags.UNLINK_ERROR).assertIsDisplayed()
            test.onNodeWithText(str(Res.string.mal_unlink_failed)).assertIsDisplayed()
            assertEquals(1, count(MalAccountTags.UNLINK_ERROR_DISMISS))
        }

    fun assertErrorHidden() =
        apply {
            assertEquals(0, count(MalAccountTags.UNLINK_ERROR))
            assertEquals(0, count(MalAccountTags.UNLINK_ERROR_DISMISS))
        }

    fun clickDismiss() = apply { test.onNodeWithTag(MalAccountTags.UNLINK_ERROR_DISMISS).performClick() }

    fun assertActions(vararg expected: MalAccountAction) = apply { assertEquals(expected.toList(), actions) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class MalAccountSectionUnlinkTest {
    @Test
    fun unlinkRowIsShownWhenLinkedAndNotConnected() =
        runComposeUiTest {
            SectionRobot(this)
                .show(MalAccountState(MalAccountStatus.NotConnected, linkedToAccount = true))
                .assertUnlinkRowShown()
        }

    @Test
    fun unlinkRowIsShownWhenLinkedAndConnected() =
        runComposeUiTest {
            SectionRobot(this)
                .show(MalAccountState(MalAccountStatus.Connected("francisco"), linkedToAccount = true))
                .assertUnlinkRowShown()
        }

    @Test
    fun unlinkRowIsShownWhenLinkedAndNeedingConsent() =
        runComposeUiTest {
            SectionRobot(this)
                .show(MalAccountState(MalAccountStatus.NeedsConsent, linkedToAccount = true))
                .assertUnlinkRowShown()
        }

    @Test
    fun unlinkRowIsHiddenWhenNotLinkedInEveryStatus() =
        runComposeUiTest {
            SectionRobot(this)
                .show(MalAccountState(MalAccountStatus.NotConnected, linkedToAccount = false))
                .assertUnlinkRowHidden()
                .show(MalAccountState(MalAccountStatus.Connected("francisco"), linkedToAccount = false))
                .assertUnlinkRowHidden()
                .show(MalAccountState(MalAccountStatus.NeedsConsent, linkedToAccount = false))
                .assertUnlinkRowHidden()
        }

    @Test
    fun unlinkRowIsHiddenWhenNotLinkedAndNotConnected() =
        runComposeUiTest {
            SectionRobot(this)
                .show(MalAccountState(MalAccountStatus.NotConnected, linkedToAccount = false))
                .assertUnlinkRowHidden()
        }

    @Test
    fun unlinkRowIsHiddenWhenNotLinkedAndNeedingConsent() =
        runComposeUiTest {
            SectionRobot(this)
                .show(MalAccountState(MalAccountStatus.NeedsConsent, linkedToAccount = false))
                .assertUnlinkRowHidden()
        }

    @Test
    fun errorBannerIsShownWhenUnlinkFailed() =
        runComposeUiTest {
            SectionRobot(this)
                .show(
                    MalAccountState(
                        MalAccountStatus.Connected("francisco"),
                        linkedToAccount = true,
                        unlinkFailed = true,
                    ),
                ).assertErrorShown()
        }

    @Test
    fun errorBannerIsHiddenWhenUnlinkDidNotFail() =
        runComposeUiTest {
            SectionRobot(this)
                .show(
                    MalAccountState(
                        MalAccountStatus.Connected("francisco"),
                        linkedToAccount = true,
                        unlinkFailed = false,
                    ),
                ).assertErrorHidden()
        }

    @Test
    fun dismissingTheErrorSendsOnUnlinkErrorDismissed() =
        runComposeUiTest {
            SectionRobot(this)
                .show(MalAccountState(MalAccountStatus.NotConnected, linkedToAccount = true, unlinkFailed = true))
                .clickDismiss()
                .assertActions(MalAccountAction.OnUnlinkErrorDismissed)
        }
}
