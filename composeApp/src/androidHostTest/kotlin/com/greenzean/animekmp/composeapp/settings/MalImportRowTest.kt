package com.greenzean.animekmp.composeapp.settings

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
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
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_added
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_conflicts
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_import
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_import_error
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_import_progress
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_import_retry
import com.greenzean.animekmp.composeapp.plural
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.mal.domain.MalImportStatus
import com.greenzean.animekmp.shared.mal.presentation.MalImportAction
import com.greenzean.animekmp.shared.mal.presentation.MalImportState
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
private class MalImportRobot(
    private val test: ComposeUiTest,
    status: MalImportStatus,
) {
    val actions = mutableListOf<MalImportAction>()

    init {
        test.setContent {
            AppTheme {
                MalImportRow(state = MalImportState(status), onAction = { actions += it })
            }
        }
    }

    private fun row() = test.onNodeWithTag(MalImportTags.ROW)

    private fun count(tag: String) = test.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    fun assertTextShown(text: String) = apply { test.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed() }

    fun assertTextMissing(text: String) =
        apply {
            assertEquals(0, test.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().size)
        }

    fun assertRowShown() = apply { row().assertIsDisplayed() }

    fun assertRowClickable() = apply { row().assertHasClickAction() }

    fun assertRowNotClickable() = apply { row().assertHasNoClickAction() }

    fun assertRowTouchHeight() = apply { row().assertHeightIsAtLeast(48.dp) }

    fun clickRow() = apply { row().performClick() }

    fun assertRetryShown() = apply { test.onNodeWithTag(MalImportTags.RETRY).assertIsDisplayed() }

    fun assertRetryMissing() = apply { assertEquals(0, count(MalImportTags.RETRY)) }

    fun assertRetryTouchHeight() = apply { test.onNodeWithTag(MalImportTags.RETRY).assertHeightIsAtLeast(48.dp) }

    fun clickRetry() = apply { test.onNodeWithTag(MalImportTags.RETRY).performClick() }

    fun assertProgressLabelled(label: String) =
        apply { test.onNodeWithContentDescription(label, useUnmergedTree = true).assertExists() }

    fun assertLiveRegion() =
        apply {
            row().assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        }

    fun assertActions(vararg expected: MalImportAction) = apply { assertEquals(expected.toList(), actions) }
}

private fun doneText(
    added: Int,
    conflicts: Int,
): String =
    listOfNotNull(
        plural(Res.plurals.settings_import_added, added, added),
        if (conflicts > 0) plural(Res.plurals.settings_import_conflicts, conflicts, conflicts) else null,
    ).joinToString("\n")

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class MalImportRowTest {
    @Test
    fun idleShowsTheTitleAndNothingElse() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Idle)
                .assertRowShown()
                .assertTextShown(str(Res.string.settings_mal_import))
                .assertTextMissing(str(Res.string.settings_mal_import_error))
                .assertRetryMissing()
        }

    @Test
    fun idleRowIsClickableAndSendsOnImportClick() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Idle)
                .assertRowClickable()
                .clickRow()
                .assertActions(MalImportAction.OnImportClick)
        }

    @Test
    fun importingShowsTheCountWithAnIndefiniteLoader() =
        runComposeUiTest {
            val progress = str(Res.string.settings_mal_import_progress, 42)

            MalImportRobot(this, MalImportStatus.Importing(42))
                .assertRowShown()
                .assertTextShown(str(Res.string.settings_mal_import))
                .assertTextShown(progress)
                .assertProgressLabelled(progress)
                .assertRetryMissing()
        }

    @Test
    fun importingCountStartsAtZero() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Importing(0))
                .assertTextShown(str(Res.string.settings_mal_import_progress, 0))
        }

    @Test
    fun importingRowCannotBeClicked() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Importing(10))
                .assertRowNotClickable()
                .clickRow()
                .assertActions()
        }

    @Test
    fun doneShowsAddedAndConflictCounts() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Done(added = 120, conflicts = 3))
                .assertTextShown(str(Res.string.settings_mal_import))
                .assertTextShown(doneText(120, 3))
                .assertRetryMissing()
        }

    @Test
    fun doneWithOneAddedAndOneConflictUsesTheSingularForms() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Done(added = 1, conflicts = 1))
                .assertTextShown(doneText(1, 1))
        }

    @Test
    fun doneWithNothingAddedStillShowsTheSummary() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Done(added = 0, conflicts = 0))
                .assertTextShown(doneText(0, 0))
        }

    @Test
    fun doneRowCanImportAgain() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Done(added = 2, conflicts = 1))
                .assertRowClickable()
                .clickRow()
                .assertActions(MalImportAction.OnImportClick)
        }

    @Test
    fun errorShowsTheMessageAndARetryButton() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Error)
                .assertRowShown()
                .assertTextShown(str(Res.string.settings_mal_import_error))
                .assertTextShown(str(Res.string.settings_mal_import_retry))
                .assertRetryShown()
        }

    @Test
    fun clickingRetrySendsOnRetryClick() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Error)
                .clickRetry()
                .assertActions(MalImportAction.OnRetryClick)
        }

    @Test
    fun retryIsNotShownOutsideTheErrorState() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Done(1, 0)).assertRetryMissing()
        }

    @Test
    fun errorReplacesTheImportTitle() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Error)
                .assertTextMissing(str(Res.string.settings_mal_import))
        }

    @Test
    fun rowMeetsTheMinimumTouchHeight() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Idle).assertRowTouchHeight()
        }

    @Test
    fun retryMeetsTheMinimumTouchHeight() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Error).assertRetryTouchHeight()
        }

    @Test
    fun doneAnnouncesChangesPolitely() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Done(1, 1)).assertLiveRegion()
        }

    @Test
    fun importingAnnouncesChangesPolitely() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Importing(3)).assertLiveRegion()
        }

    @Test
    fun errorAnnouncesChangesPolitely() =
        runComposeUiTest {
            MalImportRobot(this, MalImportStatus.Error).assertLiveRegion()
        }
}
