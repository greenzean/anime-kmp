package com.greenzean.animekmp.composeapp.library

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.mal_linked_connect
import com.greenzean.animekmp.composeapp.generated.resources.mal_linked_not_here
import com.greenzean.animekmp.composeapp.generated.resources.mal_linked_prompt
import com.greenzean.animekmp.composeapp.mal.MalAccountTags
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.library.presentation.LibraryAction
import com.greenzean.animekmp.shared.library.presentation.LibraryState
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
private class LibraryPromptRobot(
    private val test: ComposeUiTest,
    state: LibraryState,
) {
    val actions = mutableListOf<LibraryAction>()

    init {
        test.setContent { AppTheme { LibraryScreen(state = state, onAction = { actions += it }) } }
    }

    fun assertPromptShown() =
        apply {
            test.onNodeWithTag(MalAccountTags.LIBRARY_PROMPT).assertIsDisplayed()
            test.onNodeWithText(str(Res.string.mal_linked_prompt)).assertIsDisplayed()
            test.onNodeWithText(str(Res.string.mal_linked_connect)).assertIsDisplayed()
            test.onNodeWithText(str(Res.string.mal_linked_not_here)).assertIsDisplayed()
        }

    fun assertPromptMissing() =
        apply {
            listOf(MalAccountTags.LIBRARY_PROMPT, MalAccountTags.LIBRARY_CONNECT, MalAccountTags.LIBRARY_NOT_HERE)
                .forEach { assertEquals(0, test.onAllNodesWithTag(it).fetchSemanticsNodes().size) }
        }

    fun assertTouchTargets() =
        apply {
            test.onNodeWithTag(MalAccountTags.LIBRARY_CONNECT).assertHeightIsAtLeast(48.dp)
            test.onNodeWithTag(MalAccountTags.LIBRARY_NOT_HERE).assertHeightIsAtLeast(48.dp)
        }

    fun clickTag(tag: String) = apply { test.onNodeWithTag(tag).performClick() }

    fun assertActions(vararg expected: LibraryAction) = apply { assertEquals(expected.toList(), actions) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class LibraryMalPromptTest {
    @Test
    fun showsThePromptWhenAsked() =
        runComposeUiTest {
            LibraryPromptRobot(this, LibraryState(showMalPrompt = true))
                .assertPromptShown()
                .assertTouchTargets()
        }

    @Test
    fun hidesThePromptByDefault() =
        runComposeUiTest {
            LibraryPromptRobot(this, LibraryState()).assertPromptMissing()
        }

    @Test
    fun connectSendsConnectMalClick() =
        runComposeUiTest {
            LibraryPromptRobot(this, LibraryState(showMalPrompt = true))
                .clickTag(MalAccountTags.LIBRARY_CONNECT)
                .assertActions(LibraryAction.OnConnectMalClick)
        }

    @Test
    fun notOnThisDeviceSendsItsAction() =
        runComposeUiTest {
            LibraryPromptRobot(this, LibraryState(showMalPrompt = true))
                .clickTag(MalAccountTags.LIBRARY_NOT_HERE)
                .assertActions(LibraryAction.OnNotOnThisDeviceClick)
        }
}
