package com.greenzean.animekmp.composeapp.conflicts

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
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
import com.greenzean.animekmp.composeapp.generated.resources.cd_back
import com.greenzean.animekmp.composeapp.generated.resources.cd_conflicts_keep_app
import com.greenzean.animekmp.composeapp.generated.resources.cd_conflicts_keep_mal
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_app_label
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_empty_message
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_empty_title
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_keep_all_app
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_keep_all_mal
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_keep_all_mal_dialog_message
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_keep_all_mal_dialog_title
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_mal_label
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_progress
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_removed
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_score
import com.greenzean.animekmp.composeapp.generated.resources.conflicts_title
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_completed
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_watching
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.conflict.domain.EntryValues
import com.greenzean.animekmp.shared.conflict.presentation.ConflictUi
import com.greenzean.animekmp.shared.conflict.presentation.ConflictsAction
import com.greenzean.animekmp.shared.conflict.presentation.ConflictsState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

private val bebop =
    ConflictUi(
        malId = 1,
        title = "Cowboy Bebop",
        app = EntryValues(WatchStatus.WATCHING, 5, 7, removed = false),
        mal = EntryValues(WatchStatus.COMPLETED, 26, 9, removed = false),
    )

private val trigun =
    ConflictUi(
        malId = 2,
        title = "Trigun",
        app = EntryValues(WatchStatus.COMPLETED, 26, 8, removed = true),
        mal = EntryValues(WatchStatus.DROPPED, 3, null, removed = false),
    )

private val oneConflict = ConflictsState(items = listOf(bebop), isLoading = false)
private val twoConflicts = ConflictsState(items = listOf(bebop, trigun), isLoading = false)

@OptIn(ExperimentalTestApi::class)
private class ConflictsRobot(
    private val test: ComposeUiTest,
    state: ConflictsState,
) {
    val actions = mutableListOf<ConflictsAction>()
    var backClicks = 0

    init {
        test.setContent {
            AppTheme {
                ConflictsScreen(state = state, onAction = { actions += it }, onNavigateBack = { backClicks++ })
            }
        }
    }

    fun assertTextShown(text: String) = apply { test.onNodeWithText(text).assertIsDisplayed() }

    fun assertTextHidden(text: String) = apply { test.onNodeWithText(text).assertDoesNotExist() }

    fun assertTagShown(tag: String) = apply { test.onNodeWithTag(tag).assertIsDisplayed() }

    fun assertTagHidden(tag: String) = apply { test.onNodeWithTag(tag).assertDoesNotExist() }

    fun assertInItem(
        malId: Int,
        text: String,
    ) = apply {
        test
            .onNode(hasText(text) and hasAnyAncestor(hasTestTag(ConflictsTags.item(malId))), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    fun assertNotInItem(
        malId: Int,
        text: String,
    ) = apply {
        test
            .onNode(hasText(text) and hasAnyAncestor(hasTestTag(ConflictsTags.item(malId))), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    fun clickTag(tag: String) = apply { test.onNodeWithTag(tag).performClick() }

    fun clickText(text: String) = apply { test.onNodeWithText(text).performClick() }

    fun clickBack() = apply { test.onNodeWithContentDescription(str(Res.string.cd_back)).performClick() }

    fun assertDescriptionShown(description: String) =
        apply { test.onNodeWithContentDescription(description).assertIsDisplayed() }

    fun assertTagMinHeight(tag: String) = apply { test.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp) }

    fun assertActions(vararg expected: ConflictsAction) = apply { assertEquals(expected.toList(), actions) }

    fun assertBackClicks(expected: Int) = apply { assertEquals(expected, backClicks) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h1600dp")
class ConflictsScreenTest {
    @Test
    fun showsTheTitleAndTheListWithEachAnime() =
        runComposeUiTest {
            ConflictsRobot(this, twoConflicts)
                .assertTextShown(str(Res.string.conflicts_title))
                .assertTagShown(ConflictsTags.LIST)
                .assertTagShown(ConflictsTags.item(1))
                .assertTagShown(ConflictsTags.item(2))
                .assertTextShown("Cowboy Bebop")
                .assertTextShown("Trigun")
                .assertTagHidden(ConflictsTags.EMPTY)
        }

    @Test
    fun itemShowsBothVersionsWithTheirLabels() =
        runComposeUiTest {
            ConflictsRobot(this, oneConflict)
                .assertInItem(1, str(Res.string.conflicts_app_label))
                .assertInItem(1, str(Res.string.conflicts_mal_label))
                .assertInItem(1, str(Res.string.watch_status_watching))
                .assertInItem(1, str(Res.string.conflicts_progress, 5))
                .assertInItem(1, str(Res.string.conflicts_score, 7))
                .assertInItem(1, str(Res.string.watch_status_completed))
                .assertInItem(1, str(Res.string.conflicts_progress, 26))
                .assertInItem(1, str(Res.string.conflicts_score, 9))
        }

    @Test
    fun removedAppSideShowsTheRemovedLabelInsteadOfItsValues() =
        runComposeUiTest {
            ConflictsRobot(this, ConflictsState(items = listOf(trigun), isLoading = false))
                .assertInItem(2, str(Res.string.conflicts_removed))
                .assertNotInItem(2, str(Res.string.conflicts_progress, 26))
                .assertNotInItem(2, str(Res.string.conflicts_score, 8))
                .assertInItem(2, str(Res.string.conflicts_progress, 3))
        }

    @Test
    fun nonRemovedItemShowsNoRemovedLabel() =
        runComposeUiTest {
            ConflictsRobot(this, oneConflict)
                .assertNotInItem(1, str(Res.string.conflicts_removed))
        }

    @Test
    fun emptyStateIsShownWhenThereAreNoConflicts() =
        runComposeUiTest {
            ConflictsRobot(this, ConflictsState(isLoading = false))
                .assertTagShown(ConflictsTags.EMPTY)
                .assertTextShown(str(Res.string.conflicts_empty_title))
                .assertTextShown(str(Res.string.conflicts_empty_message))
                .assertTagHidden(ConflictsTags.LIST)
                .assertTagHidden(ConflictsTags.KEEP_ALL_APP)
                .assertTagHidden(ConflictsTags.KEEP_ALL_MAL)
        }

    @Test
    fun loadingShowsNeitherTheEmptyStateNorTheList() =
        runComposeUiTest {
            ConflictsRobot(this, ConflictsState(isLoading = true))
                .assertTagHidden(ConflictsTags.EMPTY)
                .assertTagHidden(ConflictsTags.LIST)
                .assertTextHidden(str(Res.string.conflicts_empty_title))
        }

    @Test
    fun keepAllButtonsAreShownAboveTheList() =
        runComposeUiTest {
            ConflictsRobot(this, oneConflict)
                .assertTagShown(ConflictsTags.KEEP_ALL_APP)
                .assertTagShown(ConflictsTags.KEEP_ALL_MAL)
                .assertTextShown(str(Res.string.conflicts_keep_all_app))
                .assertTextShown(str(Res.string.conflicts_keep_all_mal))
        }

    @Test
    fun keepAppButtonSendsKeepAppWithTheMalId() =
        runComposeUiTest {
            ConflictsRobot(this, twoConflicts)
                .clickTag(ConflictsTags.keepApp(2))
                .assertActions(ConflictsAction.OnKeepAppClick(2))
        }

    @Test
    fun keepMalButtonSendsKeepMalWithTheMalId() =
        runComposeUiTest {
            ConflictsRobot(this, twoConflicts)
                .clickTag(ConflictsTags.keepMal(1))
                .assertActions(ConflictsAction.OnKeepMalClick(1))
        }

    @Test
    fun keepAllAppSendsItsActionWithoutADialog() =
        runComposeUiTest {
            ConflictsRobot(this, twoConflicts)
                .clickTag(ConflictsTags.KEEP_ALL_APP)
                .assertActions(ConflictsAction.OnKeepAllAppClick)
        }

    @Test
    fun keepAllMalSendsItsAction() =
        runComposeUiTest {
            ConflictsRobot(this, twoConflicts)
                .clickTag(ConflictsTags.KEEP_ALL_MAL)
                .assertActions(ConflictsAction.OnKeepAllMalClick)
        }

    @Test
    fun itemButtonsHaveDescriptionsNamingTheAnime() =
        runComposeUiTest {
            ConflictsRobot(this, oneConflict)
                .assertDescriptionShown(str(Res.string.cd_conflicts_keep_app, "Cowboy Bebop"))
                .assertDescriptionShown(str(Res.string.cd_conflicts_keep_mal, "Cowboy Bebop"))
        }

    @Test
    fun itemAndKeepAllButtonsMeetTheMinimumTouchHeight() =
        runComposeUiTest {
            ConflictsRobot(this, oneConflict)
                .assertTagMinHeight(ConflictsTags.keepApp(1))
                .assertTagMinHeight(ConflictsTags.keepMal(1))
                .assertTagMinHeight(ConflictsTags.KEEP_ALL_APP)
                .assertTagMinHeight(ConflictsTags.KEEP_ALL_MAL)
        }

    @Test
    fun dialogIsClosedByDefault() =
        runComposeUiTest {
            ConflictsRobot(this, oneConflict)
                .assertTextHidden(str(Res.string.conflicts_keep_all_mal_dialog_title))
                .assertTagHidden(ConflictsTags.KEEP_ALL_MAL_CONFIRM)
        }

    @Test
    fun dialogShowsItsTitleMessageAndBothButtons() =
        runComposeUiTest {
            ConflictsRobot(this, oneConflict.copy(showKeepAllMalDialog = true))
                .assertTextShown(str(Res.string.conflicts_keep_all_mal_dialog_title))
                .assertTextShown(str(Res.string.conflicts_keep_all_mal_dialog_message))
                .assertTagShown(ConflictsTags.KEEP_ALL_MAL_CONFIRM)
                .assertTextShown(str(Res.string.action_cancel))
        }

    @Test
    fun confirmingTheDialogSendsKeepAllMalConfirm() =
        runComposeUiTest {
            ConflictsRobot(this, oneConflict.copy(showKeepAllMalDialog = true))
                .clickTag(ConflictsTags.KEEP_ALL_MAL_CONFIRM)
                .assertActions(ConflictsAction.OnKeepAllMalConfirm)
        }

    @Test
    fun cancellingTheDialogSendsDismiss() =
        runComposeUiTest {
            ConflictsRobot(this, oneConflict.copy(showKeepAllMalDialog = true))
                .clickText(str(Res.string.action_cancel))
                .assertActions(ConflictsAction.OnDismissDialog)
        }

    @Test
    fun backButtonCallsNavigateBack() =
        runComposeUiTest {
            ConflictsRobot(this, oneConflict)
                .clickBack()
                .assertBackClicks(1)
                .assertActions()
        }
}
