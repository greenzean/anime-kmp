package com.greenzean.animekmp.composeapp.details

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_cancel
import com.greenzean.animekmp.composeapp.generated.resources.cd_score_star
import com.greenzean.animekmp.composeapp.generated.resources.entry_score_label
import com.greenzean.animekmp.composeapp.generated.resources.entry_score_none
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.details.presentation.DetailsAction
import com.greenzean.animekmp.shared.details.presentation.DetailsState
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class DetailsScoreDialogTest {
    private fun ComposeUiTest.show(state: DetailsState = loadedInLibrary): Screen {
        val screen = Screen()
        setContent { AppTheme(false) { DetailsScreen(state = state, onAction = { screen.actions += it }) } }
        return screen
    }

    private fun ComposeUiTest.openDialog(state: DetailsState = loadedInLibrary): Screen {
        val screen = show(state)
        onNodeWithText(str(Res.string.entry_score_label)).performScrollTo().performClick()
        return screen
    }

    private fun star(score: Int) = hasContentDescription(str(Res.string.cd_score_star, score))

    private val noScoreButton get() = hasText(str(Res.string.entry_score_none)) and hasClickAction()

    @Test
    fun scoreRowOpensADialogTitledYourScoreWithTenStars() =
        runComposeUiTest {
            openDialog()

            onAllNodesWithText(str(Res.string.entry_score_label)).assertCountEquals(2)
            for (score in 1..10) {
                onNode(star(score)).assertIsDisplayed()
            }
            onNode(noScoreButton).assertIsDisplayed()
            onNodeWithText(str(Res.string.action_cancel)).assertDoesNotExist()
        }

    @Test
    fun starsUpToTheCurrentScoreAreSelected() =
        runComposeUiTest {
            openDialog()

            for (score in 1..7) {
                onNode(star(score)).assertIsSelected()
            }
            for (score in 8..10) {
                onNode(star(score)).assertIsNotSelected()
            }
        }

    @Test
    fun noStarIsSelectedWithoutAScore() =
        runComposeUiTest {
            openDialog(loaded.copy(entry = entry.copy(score = null)))

            for (score in 1..10) {
                onNode(star(score)).assertIsNotSelected()
            }
        }

    @Test
    fun tappingAStarSendsItAndClosesTheDialog() =
        runComposeUiTest {
            val screen = openDialog()

            onNode(star(9)).performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnScoreChange(9)), screen.actions)
            onNode(star(9)).assertDoesNotExist()
            onNodeWithText(str(Res.string.action_cancel)).assertDoesNotExist()
        }

    @Test
    fun noScoreSendsNullAndClosesTheDialog() =
        runComposeUiTest {
            val screen = openDialog()

            onNode(noScoreButton).performClick()

            assertEquals(listOf<DetailsAction>(DetailsAction.OnScoreChange(null)), screen.actions)
            onNode(star(1)).assertDoesNotExist()
            onNodeWithText(str(Res.string.action_cancel)).assertDoesNotExist()
        }

    @Test
    fun everyStarIsAtLeast56dpWideAndTall() =
        runComposeUiTest {
            openDialog()

            for (score in 1..10) {
                onNode(star(score)).assertWidthIsAtLeast(56.dp).assertHeightIsAtLeast(56.dp)
            }
        }

    @Test
    fun theScoreIsPickedWithStarsAndNotARadioList() =
        runComposeUiTest {
            openDialog()

            for (score in 1..10) {
                onNode(star(score) and hasClickAction()).assertIsDisplayed()
            }
        }

    @Test
    fun theDialogHasNoCancelButtonAndNoScoreIsItsOnlyButton() =
        runComposeUiTest {
            openDialog()

            onNodeWithText(str(Res.string.action_cancel)).assertDoesNotExist()
            onNode(noScoreButton).assertIsDisplayed()
        }

    @Test
    fun theDialogIsClosedUntilTheRowIsTapped() =
        runComposeUiTest {
            show()

            onNode(star(1)).assertDoesNotExist()
        }
}
