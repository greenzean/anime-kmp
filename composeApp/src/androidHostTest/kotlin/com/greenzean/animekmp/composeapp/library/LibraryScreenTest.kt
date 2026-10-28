package com.greenzean.animekmp.composeapp.library

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.core.label
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_library_increment
import com.greenzean.animekmp.composeapp.generated.resources.cd_library_search_clear
import com.greenzean.animekmp.composeapp.generated.resources.cd_library_sort
import com.greenzean.animekmp.composeapp.generated.resources.cd_score_badge
import com.greenzean.animekmp.composeapp.generated.resources.cd_settings
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_value
import com.greenzean.animekmp.composeapp.generated.resources.entry_progress_value_unknown
import com.greenzean.animekmp.composeapp.generated.resources.library_clear_filters
import com.greenzean.animekmp.composeapp.generated.resources.library_empty_message
import com.greenzean.animekmp.composeapp.generated.resources.library_empty_title
import com.greenzean.animekmp.composeapp.generated.resources.library_filter_all
import com.greenzean.animekmp.composeapp.generated.resources.library_no_results_message
import com.greenzean.animekmp.composeapp.generated.resources.library_no_results_title
import com.greenzean.animekmp.composeapp.generated.resources.library_row_supporting
import com.greenzean.animekmp.composeapp.generated.resources.library_search_hint
import com.greenzean.animekmp.composeapp.generated.resources.library_sort_last_updated
import com.greenzean.animekmp.composeapp.generated.resources.library_sort_score
import com.greenzean.animekmp.composeapp.generated.resources.library_sort_title
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_completed
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_plan_to_watch
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_watching
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.library.domain.LibrarySort
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.EntryUi
import com.greenzean.animekmp.shared.library.presentation.LibraryAction
import com.greenzean.animekmp.shared.library.presentation.LibraryState
import org.jetbrains.compose.resources.StringResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

private val library =
    LibraryState(
        entries =
            listOf(
                EntryUi("e1", 52991, "Frieren", null, WatchStatus.PLAN_TO_WATCH, 0, 28, null, SyncState.PENDING),
                EntryUi("e2", 9253, "Steins;Gate", null, WatchStatus.COMPLETED, 24, 24, 9, SyncState.SYNCED),
                EntryUi("e3", 1, "Cowboy Bebop", null, WatchStatus.WATCHING, 5, null, 7, SyncState.SYNCED),
                EntryUi("e4", 5114, "Fullmetal Alchemist", null, WatchStatus.ON_HOLD, 3, 64, null, SyncState.PENDING),
                EntryUi("e5", 16498, "Attack on Titan", null, WatchStatus.DROPPED, 2, 25, 4, SyncState.SYNCED),
            ),
        isLibraryEmpty = false,
    )

private val noResults = LibraryState(query = "zzz", isLibraryEmpty = false)

@OptIn(ExperimentalTestApi::class)
private class LibraryRobot(
    private val test: ComposeUiTest,
    state: LibraryState,
) {
    val actions = mutableListOf<LibraryAction>()

    init {
        test.setContent { AppTheme { LibraryScreen(state = state, onAction = { actions += it }) } }
    }

    fun assertChipLabel(
        tag: String,
        label: String,
    ) = apply { test.onNode(hasTestTag(tag) and hasText(label)).assertIsDisplayed() }

    fun assertTextShown(text: String) = apply { test.onNodeWithText(text).assertIsDisplayed() }

    fun clickText(text: String) = apply { test.onNodeWithText(text).performClick() }

    fun clickSettings() = apply { test.onNodeWithContentDescription(str(Res.string.cd_settings)).performClick() }

    fun assertBadgeShown(score: Int) =
        apply { test.onNodeWithContentDescription(str(Res.string.cd_score_badge, score)).assertIsDisplayed() }

    fun assertBadgesExactly(vararg scores: Int) =
        apply {
            for (score in 1..10) {
                val badge = test.onNodeWithContentDescription(str(Res.string.cd_score_badge, score))
                if (score in scores) badge.assertIsDisplayed() else badge.assertDoesNotExist()
            }
        }

    fun assertIncrementShown(title: String) =
        apply { test.onNodeWithContentDescription(incrementLabel(title)).assertIsDisplayed() }

    fun assertIncrementHidden(title: String) =
        apply { test.onNodeWithContentDescription(incrementLabel(title)).assertDoesNotExist() }

    fun clickIncrement(title: String) =
        apply { test.onNodeWithContentDescription(incrementLabel(title)).performClick() }

    fun assertIncrementIsTouchTarget(title: String) =
        apply {
            test
                .onNodeWithContentDescription(incrementLabel(title))
                .assertTouchWidthIsEqualTo(48.dp)
                .assertTouchHeightIsEqualTo(48.dp)
        }

    private fun incrementLabel(title: String) = str(Res.string.cd_library_increment, title)

    fun assertSettingsIsTouchTarget() =
        apply {
            test
                .onNodeWithContentDescription(str(Res.string.cd_settings))
                .assertTouchWidthIsEqualTo(48.dp)
                .assertTouchHeightIsEqualTo(48.dp)
        }

    fun assertTextHidden(text: String) = apply { test.onNodeWithText(text).assertDoesNotExist() }

    fun assertTagShown(tag: String) = apply { test.onNodeWithTag(tag).assertIsDisplayed() }

    fun assertTagHidden(tag: String) = apply { test.onNodeWithTag(tag).assertDoesNotExist() }

    fun clickTag(tag: String) = apply { test.onNodeWithTag(tag).performClick() }

    fun typeInSearch(text: String) = apply { test.onNodeWithTag("library_search").performTextInput(text) }

    fun assertDescriptionShown(description: String) =
        apply { test.onNodeWithContentDescription(description).assertIsDisplayed() }

    fun assertDescriptionHidden(description: String) =
        apply { test.onNodeWithContentDescription(description).assertDoesNotExist() }

    fun assertTagTouchTarget(tag: String) =
        apply {
            test
                .onNodeWithTag(tag)
                .assertTouchWidthIsEqualTo(48.dp)
                .assertTouchHeightIsEqualTo(48.dp)
        }

    fun assertActions(vararg expected: LibraryAction) = apply { assertEquals(expected.toList(), actions) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1000dp-h1600dp")
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

    private fun supporting(
        status: StringResource,
        progress: String,
    ) = str(Res.string.library_row_supporting, str(status), progress)

    @Test
    fun planToWatchRowShowsOnlyTheStatus() =
        runComposeUiTest {
            LibraryRobot(this, library)
            onAllNodesWithText(str(Res.string.watch_status_plan_to_watch)).assertCountEquals(2)
        }

    @Test
    fun rowsShowStatusAndProgress() =
        runComposeUiTest {
            LibraryRobot(this, library)
                .assertTextShown(
                    supporting(Res.string.watch_status_completed, str(Res.string.entry_progress_value, 24, 24)),
                )
        }

    @Test
    fun rowWithAnUnknownTotalShowsAQuestionMark() =
        runComposeUiTest {
            LibraryRobot(this, library)
                .assertTextShown(
                    supporting(Res.string.watch_status_watching, str(Res.string.entry_progress_value_unknown, 5)),
                )
        }

    @Test
    fun scoreBadgeIsShownOnlyOnScoredRowsWithTheirNumber() =
        runComposeUiTest {
            LibraryRobot(this, library).assertBadgesExactly(9, 7, 4)
        }

    @Test
    fun libraryWithoutScoresShowsNoBadge() =
        runComposeUiTest {
            val unscored = LibraryState(entries = library.entries.map { it.copy(score = null) })

            LibraryRobot(this, unscored).assertBadgesExactly()
        }

    @Test
    fun incrementStillSendsIncrementNextToTheBadge() =
        runComposeUiTest {
            LibraryRobot(this, library)
                .assertBadgeShown(7)
                .clickIncrement("Cowboy Bebop")
                .assertActions(LibraryAction.OnIncrementClick(1))
        }

    @Test
    fun incrementButtonIsShownOnlyForWatchingEntries() =
        runComposeUiTest {
            LibraryRobot(this, library)
                .assertIncrementShown("Cowboy Bebop")
                .assertIncrementHidden("Frieren")
                .assertIncrementHidden("Steins;Gate")
                .assertIncrementHidden("Fullmetal Alchemist")
                .assertIncrementHidden("Attack on Titan")
        }

    @Test
    fun incrementButtonSendsIncrementWithTheMalIdAndNotAnEntryClick() =
        runComposeUiTest {
            LibraryRobot(this, library)
                .clickIncrement("Cowboy Bebop")
                .assertActions(LibraryAction.OnIncrementClick(1))
        }

    @Test
    fun incrementButtonMeetsTheMinimumTouchTarget() =
        runComposeUiTest {
            LibraryRobot(this, library).assertIncrementIsTouchTarget("Cowboy Bebop")
        }

    @Test
    fun emptyLibraryExplainsHowToAddAnime() =
        runComposeUiTest {
            LibraryRobot(this, LibraryState())
                .assertTextShown(str(Res.string.library_empty_title))
                .assertTextShown(str(Res.string.library_empty_message))
        }

    @Test
    fun searchFieldShowsItsHintWhenEmpty() =
        runComposeUiTest {
            LibraryRobot(this, library)
                .assertTagShown("library_search")
                .assertTextShown(str(Res.string.library_search_hint))
                .assertTagHidden("library_search_clear")
        }

    @Test
    fun typingInTheSearchFieldSendsQueryChange() =
        runComposeUiTest {
            LibraryRobot(this, library).typeInSearch("bebop").assertActions(LibraryAction.OnQueryChange("bebop"))
        }

    @Test
    fun searchFieldShowsTheQueryFromState() =
        runComposeUiTest {
            LibraryRobot(this, library.copy(query = "frieren")).assertTextShown("frieren")
        }

    @Test
    fun clearButtonAppearsWithAQueryAndSendsAnEmptyQuery() =
        runComposeUiTest {
            LibraryRobot(this, library.copy(query = "frieren"))
                .assertDescriptionShown(str(Res.string.cd_library_search_clear))
                .clickTag("library_search_clear")
                .assertActions(LibraryAction.OnQueryChange(""))
        }

    @Test
    fun clearButtonMeetsTheMinimumTouchTarget() =
        runComposeUiTest {
            LibraryRobot(this, library.copy(query = "frieren")).assertTagTouchTarget("library_search_clear")
        }

    @Test
    fun statusChipsShowAllAndEveryStatus() =
        runComposeUiTest {
            val robot = LibraryRobot(this, library).assertTagShown("library_chip_all")
            robot.assertTextShown(str(Res.string.library_filter_all))
            WatchStatus.entries.forEach { robot.assertTagShown("library_chip_${it.name}") }
        }

    @Test
    fun clickingAStatusChipSendsStatusFilterClick() =
        runComposeUiTest {
            LibraryRobot(this, library)
                .clickTag("library_chip_${WatchStatus.COMPLETED.name}")
                .assertActions(LibraryAction.OnStatusFilterClick(WatchStatus.COMPLETED))
        }

    @Test
    fun clickingAllSendsStatusFilterClickWithNull() =
        runComposeUiTest {
            LibraryRobot(this, library.copy(statusFilter = WatchStatus.WATCHING))
                .clickTag("library_chip_all")
                .assertActions(LibraryAction.OnStatusFilterClick(null))
        }

    @Test
    fun statusChipsHaveReadableLabels() =
        runComposeUiTest {
            val robot = LibraryRobot(this, library)
            WatchStatus.entries.forEach { robot.assertChipLabel("library_chip_${it.name}", str(it.label())) }
        }

    @Test
    fun sortButtonHasAnAccessibleLabelAndOpensTheOptions() =
        runComposeUiTest {
            LibraryRobot(this, library)
                .assertDescriptionShown(str(Res.string.cd_library_sort))
                .assertTagHidden("library_sort_option_TITLE")
                .clickTag("library_sort_button")
            LibrarySort.entries.forEach { onNodeWithTag("library_sort_option_${it.name}").assertIsDisplayed() }
            onNodeWithText(str(Res.string.library_sort_last_updated)).assertIsDisplayed()
            onNodeWithText(str(Res.string.library_sort_title)).assertIsDisplayed()
            onNodeWithText(str(Res.string.library_sort_score)).assertIsDisplayed()
        }

    @Test
    fun choosingASortOptionSendsSortChange() =
        runComposeUiTest {
            LibraryRobot(this, library)
                .clickTag("library_sort_button")
                .clickTag("library_sort_option_SCORE")
                .assertActions(LibraryAction.OnSortChange(LibrarySort.SCORE))
        }

    @Test
    fun choosingTitleSortSendsSortChangeWithTitle() =
        runComposeUiTest {
            LibraryRobot(this, library)
                .clickTag("library_sort_button")
                .clickTag("library_sort_option_TITLE")
                .assertActions(LibraryAction.OnSortChange(LibrarySort.TITLE))
        }

    @Test
    fun noResultsShowsTheNoResultsStateInsteadOfTheList() =
        runComposeUiTest {
            LibraryRobot(this, noResults)
                .assertTagShown("library_no_results")
                .assertTextShown(str(Res.string.library_no_results_title))
                .assertTextShown(str(Res.string.library_no_results_message))
                .assertTagShown("library_clear_filters")
                .assertTextShown(str(Res.string.library_clear_filters))
                .assertTagShown("library_search")
                .assertTagShown("library_chip_all")
        }

    @Test
    fun noResultsDoesNotShowTheEmptyLibraryMessage() =
        runComposeUiTest {
            LibraryRobot(this, noResults).assertTextHidden(str(Res.string.library_empty_title))
        }

    @Test
    fun clearFiltersResetsTheQueryAndTheStatusFilter() =
        runComposeUiTest {
            LibraryRobot(this, noResults.copy(statusFilter = WatchStatus.DROPPED))
                .clickTag("library_clear_filters")
                .assertActions(LibraryAction.OnQueryChange(""), LibraryAction.OnStatusFilterClick(null))
        }

    @Test
    fun listStateHidesTheNoResultsState() =
        runComposeUiTest {
            LibraryRobot(this, library)
                .assertTagHidden("library_no_results")
                .assertTagHidden("library_clear_filters")
        }

    @Test
    fun emptyLibraryHidesTheSearchControlsAndNoResults() =
        runComposeUiTest {
            LibraryRobot(this, LibraryState())
                .assertTagHidden("library_search")
                .assertTagHidden("library_chip_all")
                .assertTagHidden("library_sort_button")
                .assertTagHidden("library_no_results")
        }

    @Test
    fun settingsButtonMeetsTheMinimumTouchTarget() =
        runComposeUiTest {
            LibraryRobot(this, library).assertSettingsIsTouchTarget()
        }
}
