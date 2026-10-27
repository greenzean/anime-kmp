package com.greenzean.animekmp.composeapp.foryou

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_back
import com.greenzean.animekmp.composeapp.generated.resources.for_you_coming_soon_message
import com.greenzean.animekmp.composeapp.generated.resources.for_you_coming_soon_title
import com.greenzean.animekmp.composeapp.generated.resources.for_you_title
import com.greenzean.animekmp.composeapp.str
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
private class ForYouRobot(
    private val test: ComposeUiTest,
) {
    init {
        test.setContent { AppTheme { ForYouScreen() } }
    }

    fun assertTextShown(text: String) = apply { test.onNodeWithText(text).assertIsDisplayed() }

    fun assertTitleIsHeading() =
        apply {
            test
                .onNodeWithText(
                    str(Res.string.for_you_title),
                ).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }

    fun assertNoControls() = apply { assertEquals(0, test.onAllNodes(hasClickAction()).fetchSemanticsNodes().size) }

    fun assertNoBackButton() =
        apply {
            assertEquals(
                0,
                test.onAllNodes(hasContentDescription(str(Res.string.cd_back))).fetchSemanticsNodes().size,
            )
        }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ForYouScreenTest {
    @Test
    fun showsComingSoonState() =
        runComposeUiTest {
            ForYouRobot(this)
                .assertTextShown(str(Res.string.for_you_coming_soon_title))
                .assertTextShown(str(Res.string.for_you_coming_soon_message))
        }

    @Test
    fun titleIsAHeading() =
        runComposeUiTest {
            ForYouRobot(this).assertTextShown(str(Res.string.for_you_title)).assertTitleIsHeading()
        }

    @Test
    fun hasNoControlsBecauseNothingCanBeDoneYet() =
        runComposeUiTest {
            ForYouRobot(this).assertNoControls()
        }

    @Test
    fun isATopLevelTabWithoutBackButton() =
        runComposeUiTest {
            ForYouRobot(this).assertNoBackButton()
        }
}
