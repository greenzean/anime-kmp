package com.greenzean.animekmp.composeapp.settings

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_import_mal
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_added
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_conflicts
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_error_not_mal
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_error_unreadable
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_importing
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_invalid
import com.greenzean.animekmp.composeapp.generated.resources.settings_import_mal
import com.greenzean.animekmp.composeapp.plural
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.export.presentation.ExportState
import com.greenzean.animekmp.shared.importer.domain.ImportError
import com.greenzean.animekmp.shared.importer.presentation.ImportFileAction
import com.greenzean.animekmp.shared.importer.presentation.ImportFileState
import com.greenzean.animekmp.shared.importer.presentation.ImportStatus
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
private class ImportSectionRobot(
    private val test: ComposeUiTest,
    status: ImportStatus,
) {
    val actions = mutableListOf<ImportFileAction>()

    init {
        test.setContent {
            AppTheme {
                LibrarySection(
                    state = ExportState(),
                    onAction = {},
                    importState = ImportFileState(status),
                    onImportAction = { actions += it },
                )
            }
        }
    }

    private fun row() = test.onNodeWithTag(ImportTags.ROW)

    private fun missing(tag: String) = assertEquals(0, test.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size)

    fun assertTextShown(text: String) = apply { test.onNodeWithText(text).assertIsDisplayed() }

    fun assertRowLabelShown() =
        apply { test.onNodeWithContentDescription(str(Res.string.cd_import_mal)).assertExists() }

    fun assertProgressLabelShown() =
        apply { test.onNodeWithContentDescription(str(Res.string.settings_import_importing)).assertExists() }

    fun assertRowEnabled() = apply { row().assertIsEnabled() }

    fun assertRowDisabled() = apply { row().assertHasNoClickAction() }

    fun assertRowTouchHeight() = apply { row().assertHeightIsAtLeast(48.dp) }

    fun clickRow() = apply { row().performClick() }

    fun assertTagShown(tag: String) = apply { test.onNodeWithTag(tag).assertIsDisplayed() }

    fun assertTagMissing(tag: String) = apply { missing(tag) }

    fun assertTagText(
        tag: String,
        text: String,
    ) = apply { test.onNodeWithTag(tag).assertTextEquals(text) }

    fun assertLiveRegion(tag: String) =
        apply {
            test
                .onNodeWithTag(tag)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        }

    fun assertActions(vararg expected: ImportFileAction) = apply { assertEquals(expected.toList(), actions) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class ImportLibrarySectionTest {
    @Test
    fun idleShowsEnabledRowWithoutStatus() =
        runComposeUiTest {
            ImportSectionRobot(this, ImportStatus.Idle)
                .assertTextShown(str(Res.string.settings_import_mal))
                .assertRowEnabled()
                .assertTagMissing(ImportTags.PROGRESS)
                .assertTagMissing(ImportTags.DONE)
                .assertTagMissing(ImportTags.ERROR)
        }

    @Test
    fun clickingRowSendsOnImportClick() =
        runComposeUiTest {
            ImportSectionRobot(this, ImportStatus.Idle)
                .clickRow()
                .assertActions(ImportFileAction.OnImportClick)
        }

    @Test
    fun importingShowsProgressAndDisablesRow() =
        runComposeUiTest {
            ImportSectionRobot(this, ImportStatus.Importing(0.5f))
                .assertTagShown(ImportTags.PROGRESS)
                .assertProgressLabelShown()
                .assertRowDisabled()
                .assertTagMissing(ImportTags.DONE)
                .assertTagMissing(ImportTags.ERROR)
        }

    @Test
    fun clickingRowWhileImportingSendsNothing() =
        runComposeUiTest {
            ImportSectionRobot(this, ImportStatus.Importing(0.2f))
                .clickRow()
                .assertActions()
        }

    @Test
    fun doneShowsAddedConflictsAndInvalidLines() =
        runComposeUiTest {
            val expected =
                listOf(
                    plural(Res.plurals.settings_import_added, 12, 12),
                    plural(Res.plurals.settings_import_conflicts, 3, 3),
                    plural(Res.plurals.settings_import_invalid, 2, 2),
                ).joinToString("\n")

            ImportSectionRobot(this, ImportStatus.Done(added = 12, conflicts = 3, invalid = 2))
                .assertTagShown(ImportTags.DONE)
                .assertTagText(ImportTags.DONE, expected)
                .assertTagMissing(ImportTags.PROGRESS)
                .assertTagMissing(ImportTags.ERROR)
                .assertRowEnabled()
        }

    @Test
    fun doneWithOnlyAddedHidesConflictsAndInvalidLines() =
        runComposeUiTest {
            ImportSectionRobot(this, ImportStatus.Done(added = 1, conflicts = 0, invalid = 0))
                .assertTagText(ImportTags.DONE, plural(Res.plurals.settings_import_added, 1, 1))
        }

    @Test
    fun doneWithZeroAddedStillShowsSummary() =
        runComposeUiTest {
            ImportSectionRobot(this, ImportStatus.Done(added = 0, conflicts = 4, invalid = 0))
                .assertTagShown(ImportTags.DONE)
        }

    @Test
    fun unreadableErrorShowsItsMessage() =
        runComposeUiTest {
            ImportSectionRobot(this, ImportStatus.Error(ImportError.Unreadable))
                .assertTagShown(ImportTags.ERROR)
                .assertTextShown(str(Res.string.settings_import_error_unreadable))
                .assertTagMissing(ImportTags.DONE)
                .assertTagMissing(ImportTags.PROGRESS)
        }

    @Test
    fun notMalExportErrorShowsItsMessage() =
        runComposeUiTest {
            ImportSectionRobot(this, ImportStatus.Error(ImportError.NotMalExport))
                .assertTagShown(ImportTags.ERROR)
                .assertTextShown(str(Res.string.settings_import_error_not_mal))
        }

    @Test
    fun rowIsEnabledAndClickableAfterErrorForRetry() =
        runComposeUiTest {
            ImportSectionRobot(this, ImportStatus.Error(ImportError.Unreadable))
                .assertRowEnabled()
                .clickRow()
                .assertActions(ImportFileAction.OnImportClick)
        }

    @Test
    fun doneIsLiveRegion() =
        runComposeUiTest {
            ImportSectionRobot(this, ImportStatus.Done(1, 0, 0)).assertLiveRegion(ImportTags.DONE)
        }

    @Test
    fun errorIsLiveRegion() =
        runComposeUiTest {
            ImportSectionRobot(this, ImportStatus.Error(ImportError.NotMalExport)).assertLiveRegion(ImportTags.ERROR)
        }

    @Test
    fun rowHasContentDescriptionAndMinimumTouchHeight() =
        runComposeUiTest {
            ImportSectionRobot(this, ImportStatus.Idle)
                .assertRowLabelShown()
                .assertRowTouchHeight()
        }
}
