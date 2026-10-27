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
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_settings
import com.greenzean.animekmp.composeapp.generated.resources.library_empty_message
import com.greenzean.animekmp.composeapp.generated.resources.library_empty_title
import com.greenzean.animekmp.composeapp.generated.resources.library_row_supporting
import com.greenzean.animekmp.composeapp.generated.resources.sync_state_pending
import com.greenzean.animekmp.composeapp.generated.resources.sync_state_synced
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_completed
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_plan_to_watch
import com.greenzean.animekmp.composeapp.str
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

    fun clickSettings() = apply { test.onNodeWithContentDescription(str(Res.string.cd_settings)).performClick() }

    fun assertSettingsIsTouchTarget() =
        apply {
            test
                .onNodeWithContentDescription(str(Res.string.cd_settings))
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
                .assertTextShown(
                    str(
                        Res.string.library_row_supporting,
                        str(Res.string.watch_status_plan_to_watch),
                        str(Res.string.sync_state_pending),
                    ),
                ).assertTextShown(
                    str(
                        Res.string.library_row_supporting,
                        str(Res.string.watch_status_completed),
                        str(Res.string.sync_state_synced),
                    ),
                )
        }

    @Test
    fun emptyLibraryExplainsHowToAddAnime() =
        runComposeUiTest {
            LibraryRobot(this, LibraryState())
                .assertTextShown(str(Res.string.library_empty_title))
                .assertTextShown(str(Res.string.library_empty_message))
        }

    @Test
    fun settingsButtonMeetsTheMinimumTouchTarget() =
        runComposeUiTest {
            LibraryRobot(this, library).assertSettingsIsTouchTarget()
        }
}
