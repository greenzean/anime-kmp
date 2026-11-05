package com.greenzean.animekmp.composeapp.settings

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
import com.greenzean.animekmp.composeapp.generated.resources.action_cancel
import com.greenzean.animekmp.composeapp.generated.resources.mal_unlink_dialog_title
import com.greenzean.animekmp.composeapp.generated.resources.mal_unlink_row
import com.greenzean.animekmp.composeapp.mal.MalAccountTags
import com.greenzean.animekmp.composeapp.str
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
private class UnlinkRobot(
    private val test: ComposeUiTest,
) {
    var confirms = 0

    init {
        test.setContent { AppTheme { MalUnlinkRow(onConfirm = { confirms++ }) } }
    }

    private fun count(tag: String) = test.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    fun assertRowLabelled() = apply { test.onNodeWithText(str(Res.string.mal_unlink_row)).assertIsDisplayed() }

    fun assertTouchTarget() = apply { test.onNodeWithTag(MalAccountTags.UNLINK).assertHeightIsAtLeast(48.dp) }

    fun clickRow() = apply { test.onNodeWithTag(MalAccountTags.UNLINK).performClick() }

    fun clickConfirm() = apply { test.onNodeWithTag(MalAccountTags.UNLINK_CONFIRM).performClick() }

    fun clickCancel() = apply { test.onNodeWithText(str(Res.string.action_cancel)).performClick() }

    fun assertDialogShown() =
        apply {
            test.onNodeWithText(str(Res.string.mal_unlink_dialog_title)).assertIsDisplayed()
            assertEquals(1, count(MalAccountTags.UNLINK_CONFIRM))
        }

    fun assertDialogMissing() = apply { assertEquals(0, count(MalAccountTags.UNLINK_CONFIRM)) }

    fun assertConfirms(expected: Int) = apply { assertEquals(expected, confirms) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class MalUnlinkRowTest {
    @Test
    fun rowIsLabelledAndATouchTarget() =
        runComposeUiTest {
            UnlinkRobot(this).assertRowLabelled().assertTouchTarget().assertDialogMissing()
        }

    @Test
    fun clickingTheRowOpensTheDialogWithoutUnlinking() =
        runComposeUiTest {
            UnlinkRobot(this).clickRow().assertDialogShown().assertConfirms(0)
        }

    @Test
    fun confirmingUnlinksAndClosesTheDialog() =
        runComposeUiTest {
            UnlinkRobot(this)
                .clickRow()
                .clickConfirm()
                .assertConfirms(1)
                .assertDialogMissing()
        }

    @Test
    fun cancellingClosesTheDialogWithoutUnlinking() =
        runComposeUiTest {
            UnlinkRobot(this)
                .clickRow()
                .clickCancel()
                .assertDialogMissing()
                .assertConfirms(0)
        }
}
