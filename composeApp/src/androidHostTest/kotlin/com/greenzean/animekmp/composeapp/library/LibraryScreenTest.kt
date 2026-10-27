package com.greenzean.animekmp.composeapp.library

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.EntryUi
import com.greenzean.animekmp.shared.library.presentation.LibraryAction
import com.greenzean.animekmp.shared.library.presentation.LibraryState
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

private val library =
    LibraryState(
        entries =
            listOf(
                EntryUi("e1", 52991, "Frieren", null, WatchStatus.PLAN_TO_WATCH, SyncState.PENDING),
                EntryUi("e2", 9253, "Steins;Gate", null, WatchStatus.COMPLETED, SyncState.SYNCED),
            ),
    )

@OptIn(ExperimentalTestApi::class)
private class LibraryRobot(
    private val test: ComposeUiTest,
    state: LibraryState,
) {
    val actions = mutableListOf<LibraryAction>()

    init {
        test.setContent { AppTheme { LibraryScreen(state = state, onAction = { actions += it }) } }
    }

    fun assertTextShown(text: String) = apply { test.onNodeWithText(text).assertIsDisplayed() }

    fun clickText(text: String) = apply { test.onNodeWithText(text).performClick() }

    fun clickSettings() = apply { test.onNodeWithContentDescription("Settings").performClick() }

    fun assertSettingsIsTouchTarget() =
        apply {
            test
                .onNodeWithContentDescription("Settings")
                .assertTouchWidthIsEqualTo(48.dp)
                .assertTouchHeightIsEqualTo(48.dp)
        }

    fun assertActions(vararg expected: LibraryAction) = apply { assertEquals(expected.toList(), actions) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryScreenTest {
    @Test
    fun tappingAnEntrySendsEntryClickWithItsMalId() =
        runComposeUiTest {
            LibraryRobot(this, library).clickText("Steins;Gate").assertActions(LibraryAction.OnEntryClick(9253))
        }

    @Test
    fun settingsButtonSendsSettingsClick() =
        runComposeUiTest {
            LibraryRobot(this, library).clickSettings().assertActions(LibraryAction.OnSettingsClick)
        }

    @Test
    fun rowsShowStatusAndSyncState() =
        runComposeUiTest {
            LibraryRobot(this, library)
                .assertTextShown("Plan to watch · Pending")
                .assertTextShown("Completed · Synced")
        }

    @Test
    fun emptyLibraryExplainsHowToAddAnime() =
        runComposeUiTest {
            LibraryRobot(this, LibraryState())
                .assertTextShown("Your library is empty")
                .assertTextShown("Find an anime in the Search tab and add it here.")
        }

    @Test
    fun settingsButtonMeetsTheMinimumTouchTarget() =
        runComposeUiTest {
            LibraryRobot(this, library).assertSettingsIsTouchTarget()
        }
}
