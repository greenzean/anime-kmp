package com.greenzean.animekmp.composeapp.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.conflicts.ConflictsTags
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_export_mal
import com.greenzean.animekmp.composeapp.generated.resources.settings_conflicts_row
import com.greenzean.animekmp.composeapp.generated.resources.settings_conflicts_supporting
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_done
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_error_empty
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_error_write
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_exporting
import com.greenzean.animekmp.composeapp.generated.resources.settings_export_mal
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_added
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_conflicts
import com.greenzean.animekmp.composeapp.generated.resources.settings_library_title
import com.greenzean.animekmp.composeapp.plural
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.export.domain.ExportError
import com.greenzean.animekmp.shared.export.presentation.ExportAction
import com.greenzean.animekmp.shared.export.presentation.ExportState
import com.greenzean.animekmp.shared.export.presentation.ExportStatus
import com.greenzean.animekmp.shared.importer.presentation.ImportFileAction
import com.greenzean.animekmp.shared.importer.presentation.ImportFileState
import com.greenzean.animekmp.shared.importer.presentation.ImportStatus
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
private class LibrarySectionRobot(
    private val test: ComposeUiTest,
    status: ExportStatus,
) {
    val actions = mutableListOf<ExportAction>()

    init {
        test.setContent {
            AppTheme {
                LibrarySection(
                    state = ExportState(status),
                    onAction = { actions += it },
                    importState = ImportFileState(),
                    onImportAction = {},
                )
            }
        }
    }

    private fun row() = test.onNodeWithTag(ExportTags.ROW)

    private fun missing(tag: String) = assertEquals(0, test.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size)

    fun assertHeading(text: String) =
        apply {
            test
                .onNode(hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                .assertExists()
        }

    fun assertTextShown(text: String) = apply { test.onNodeWithText(text).assertIsDisplayed() }

    fun assertRowLabelShown() =
        apply { test.onNodeWithContentDescription(str(Res.string.cd_export_mal)).assertExists() }

    fun assertProgressLabelShown() =
        apply { test.onNodeWithContentDescription(str(Res.string.settings_export_exporting)).assertExists() }

    fun assertRowEnabled() = apply { row().assertIsEnabled() }

    fun assertRowDisabled() = apply { row().assertHasNoClickAction() }

    fun assertRowTouchHeight() = apply { row().assertHeightIsAtLeast(48.dp) }

    fun clickRow() = apply { row().performClick() }

    fun assertTagShown(tag: String) = apply { test.onNodeWithTag(tag).assertIsDisplayed() }

    fun assertTagMissing(tag: String) = apply { missing(tag) }

    fun assertActions(vararg expected: ExportAction) = apply { assertEquals(expected.toList(), actions) }
}

@OptIn(ExperimentalTestApi::class)
private class ConflictsRowRobot(
    private val test: ComposeUiTest,
    importState: ImportFileState,
) {
    val actions = mutableListOf<ImportFileAction>()

    init {
        test.setContent {
            AppTheme {
                LibrarySection(
                    state = ExportState(),
                    onAction = {},
                    importState = importState,
                    onImportAction = { actions += it },
                )
            }
        }
    }

    private fun row() = test.onNodeWithTag(ConflictsTags.SETTINGS_ROW)

    fun assertRowShown() = apply { row().assertIsDisplayed() }

    fun assertRowTitleShown() =
        apply { test.onNodeWithText(str(Res.string.settings_conflicts_row)).assertIsDisplayed() }

    fun assertSupportingCount(count: Int) =
        apply {
            test
                .onNodeWithText(plural(Res.plurals.settings_conflicts_supporting, count, count))
                .assertIsDisplayed()
        }

    fun assertRowTouchHeight() = apply { row().assertHeightIsAtLeast(48.dp) }

    fun clickRow() = apply { row().performClick() }

    fun assertTextShown(text: String) = apply { test.onNodeWithText(text).assertIsDisplayed() }

    fun assertActions(vararg expected: ImportFileAction) = apply { assertEquals(expected.toList(), actions) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class LibrarySectionTest {
    @Test
    fun conflictsRowIsShownBelowImportWithItsTitle() =
        runComposeUiTest {
            ConflictsRowRobot(this, ImportFileState())
                .assertRowShown()
                .assertRowTitleShown()
        }

    @Test
    fun conflictsRowShowsThePluralisedCount() =
        runComposeUiTest {
            ConflictsRowRobot(this, ImportFileState(conflictCount = 3))
                .assertSupportingCount(3)
        }

    @Test
    fun conflictsRowWithOneConflictUsesTheSingularText() =
        runComposeUiTest {
            ConflictsRowRobot(this, ImportFileState(conflictCount = 1))
                .assertSupportingCount(1)
        }

    @Test
    fun clickingTheConflictsRowSendsOnConflictsClick() =
        runComposeUiTest {
            ConflictsRowRobot(this, ImportFileState(conflictCount = 2))
                .clickRow()
                .assertActions(ImportFileAction.OnConflictsClick)
        }

    @Test
    fun conflictsRowMeetsTheMinimumTouchHeight() =
        runComposeUiTest {
            ConflictsRowRobot(this, ImportFileState(conflictCount = 2)).assertRowTouchHeight()
        }

    @Test
    fun importSummaryShowsTheConflictsLine() =
        runComposeUiTest {
            ConflictsRowRobot(
                this,
                ImportFileState(ImportStatus.Done(added = 4, conflicts = 2, invalid = 0), conflictCount = 2),
            ).assertTextShown(
                listOf(
                    plural(Res.plurals.settings_import_added, 4, 4),
                    plural(Res.plurals.settings_import_conflicts, 2, 2),
                ).joinToString("\n"),
            )
        }

    @Test
    fun importSummaryDropsTheConflictsLineOnceNoneArePending() =
        runComposeUiTest {
            ConflictsRowRobot(
                this,
                ImportFileState(ImportStatus.Done(added = 4, conflicts = 2, invalid = 0), conflictCount = 0),
            ).assertTextShown(plural(Res.plurals.settings_import_added, 4, 4))
        }

    @Test
    fun importSummaryShowsOnlyTheConflictsStillPending() =
        runComposeUiTest {
            ConflictsRowRobot(
                this,
                ImportFileState(ImportStatus.Done(added = 4, conflicts = 3, invalid = 0), conflictCount = 1),
            ).assertTextShown(
                listOf(
                    plural(Res.plurals.settings_import_added, 4, 4),
                    plural(Res.plurals.settings_import_conflicts, 1, 1),
                ).joinToString("\n"),
            )
        }

    @Test
    fun idleShowsHeadingAndEnabledRowWithoutStatus() =
        runComposeUiTest {
            LibrarySectionRobot(this, ExportStatus.Idle)
                .assertHeading(str(Res.string.settings_library_title))
                .assertTextShown(str(Res.string.settings_export_mal))
                .assertRowEnabled()
                .assertTagMissing(ExportTags.PROGRESS)
                .assertTagMissing(ExportTags.DONE)
                .assertTagMissing(ExportTags.ERROR)
        }

    @Test
    fun clickingRowSendsOnExportClick() =
        runComposeUiTest {
            LibrarySectionRobot(this, ExportStatus.Idle)
                .clickRow()
                .assertActions(ExportAction.OnExportClick)
        }

    @Test
    fun exportingShowsProgressAndDisablesRow() =
        runComposeUiTest {
            LibrarySectionRobot(this, ExportStatus.Exporting)
                .assertTagShown(ExportTags.PROGRESS)
                .assertProgressLabelShown()
                .assertRowDisabled()
                .assertTagMissing(ExportTags.DONE)
                .assertTagMissing(ExportTags.ERROR)
        }

    @Test
    fun clickingRowWhileExportingSendsNothing() =
        runComposeUiTest {
            LibrarySectionRobot(this, ExportStatus.Exporting)
                .clickRow()
                .assertActions()
        }

    @Test
    fun doneShowsExportedCountAndHidesProgress() =
        runComposeUiTest {
            LibrarySectionRobot(this, ExportStatus.Done(42))
                .assertTagShown(ExportTags.DONE)
                .assertTextShown(plural(Res.plurals.settings_export_done, 42, 42))
                .assertTagMissing(ExportTags.PROGRESS)
                .assertTagMissing(ExportTags.ERROR)
                .assertRowEnabled()
        }

    @Test
    fun doneWithOneEntryUsesSingularText() =
        runComposeUiTest {
            LibrarySectionRobot(this, ExportStatus.Done(1))
                .assertTextShown(plural(Res.plurals.settings_export_done, 1, 1))
        }

    @Test
    fun emptyLibraryErrorShowsItsMessage() =
        runComposeUiTest {
            LibrarySectionRobot(this, ExportStatus.Error(ExportError.EmptyLibrary))
                .assertTagShown(ExportTags.ERROR)
                .assertTextShown(str(Res.string.settings_export_error_empty))
                .assertTagMissing(ExportTags.DONE)
                .assertTagMissing(ExportTags.PROGRESS)
        }

    @Test
    fun writeFailedErrorShowsItsMessage() =
        runComposeUiTest {
            LibrarySectionRobot(this, ExportStatus.Error(ExportError.WriteFailed))
                .assertTagShown(ExportTags.ERROR)
                .assertTextShown(str(Res.string.settings_export_error_write))
        }

    @Test
    fun rowIsEnabledAndClickableAfterErrorForRetry() =
        runComposeUiTest {
            LibrarySectionRobot(this, ExportStatus.Error(ExportError.WriteFailed))
                .assertRowEnabled()
                .clickRow()
                .assertActions(ExportAction.OnExportClick)
        }

    @Test
    fun rowHasContentDescriptionAndMinimumTouchHeight() =
        runComposeUiTest {
            LibrarySectionRobot(this, ExportStatus.Idle)
                .assertRowLabelShown()
                .assertRowTouchHeight()
        }
}
