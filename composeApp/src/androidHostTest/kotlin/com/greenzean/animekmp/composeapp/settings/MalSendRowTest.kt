package com.greenzean.animekmp.composeapp.settings

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_send_failed
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_send_pending
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_send_retry
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_send_up_to_date
import com.greenzean.animekmp.composeapp.plural
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.mal.domain.MalSendError
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
private class MalSendRobot(
    private val test: ComposeUiTest,
    pendingCount: Int = 0,
    lastError: MalSendError? = null,
) {
    var retryClicks = 0

    init {
        test.setContent {
            AppTheme {
                MalSendRow(pendingCount = pendingCount, lastError = lastError, onRetryClick = { retryClicks++ })
            }
        }
    }

    private fun count(tag: String) = test.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    fun assertStatus(text: String) =
        apply {
            test.onNodeWithTag(MalSendTags.STATUS).assertIsDisplayed().assertTextEquals(text)
        }

    fun assertTextMissing(text: String) =
        apply {
            assertEquals(0, test.onAllNodes(hasText(text)).fetchSemanticsNodes().size)
        }

    fun assertRetryShown() =
        apply {
            test.onNodeWithTag(MalSendTags.RETRY).assertIsDisplayed()
            test.onNodeWithText(str(Res.string.settings_mal_send_retry)).assertIsDisplayed()
        }

    fun assertRetryMissing() = apply { assertEquals(0, count(MalSendTags.RETRY)) }

    fun assertRetryTouchHeight() = apply { test.onNodeWithTag(MalSendTags.RETRY).assertHeightIsAtLeast(48.dp) }

    fun clickRetry() = apply { test.onNodeWithTag(MalSendTags.RETRY).performClick() }

    fun assertRetryClicks(expected: Int) = apply { assertEquals(expected, retryClicks) }
}

private fun pendingText(count: Int): String = plural(Res.plurals.settings_mal_send_pending, count, count)

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class MalSendRowTest {
    @Test
    fun nothingPendingShowsUpToDateWithoutRetry() =
        runComposeUiTest {
            MalSendRobot(this)
                .assertStatus(str(Res.string.settings_mal_send_up_to_date))
                .assertRetryMissing()
        }

    @Test
    fun pendingChangesShowTheCount() =
        runComposeUiTest {
            MalSendRobot(this, pendingCount = 3)
                .assertStatus(pendingText(3))
                .assertRetryMissing()
        }

    @Test
    fun oneChangeUsesTheSingularForm() =
        runComposeUiTest {
            MalSendRobot(this, pendingCount = 1)
                .assertStatus(pendingText(1))
                .assertTextMissing(str(Res.string.settings_mal_send_up_to_date))
        }

    @Test
    fun anErrorShowsSendingFailedAndRetry() =
        runComposeUiTest {
            MalSendRobot(this, pendingCount = 2, lastError = MalSendError.NO_CONNECTION)
                .assertStatus(str(Res.string.settings_mal_send_failed))
                .assertRetryShown()
        }

    @Test
    fun anErrorWinsOverThePendingCount() =
        runComposeUiTest {
            MalSendRobot(this, pendingCount = 5, lastError = MalSendError.SERVER)
                .assertStatus(str(Res.string.settings_mal_send_failed))
                .assertTextMissing(pendingText(5))
        }

    @Test
    fun clickingRetryCallsTheCallback() =
        runComposeUiTest {
            MalSendRobot(this, lastError = MalSendError.THROTTLED)
                .clickRetry()
                .assertRetryClicks(1)
        }

    @Test
    fun retryIsMissingWhenThereIsNoError() =
        runComposeUiTest {
            MalSendRobot(this, pendingCount = 4).assertRetryMissing().assertRetryClicks(0)
        }

    @Test
    fun retryMeetsTheMinimumTouchHeight() =
        runComposeUiTest {
            MalSendRobot(this, lastError = MalSendError.BLOCKED).assertRetryTouchHeight()
        }
}
