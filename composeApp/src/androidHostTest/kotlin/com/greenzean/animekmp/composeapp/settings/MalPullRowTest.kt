package com.greenzean.animekmp.composeapp.settings

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.cd_in_progress
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_last_synced_days
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_last_synced_hours
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_last_synced_just_now
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_last_synced_minutes
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_last_synced_never
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_sync_now
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_syncing
import com.greenzean.animekmp.composeapp.plural
import com.greenzean.animekmp.composeapp.str
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val now = Instant.parse("2026-11-03T12:00:00Z")

@OptIn(ExperimentalTestApi::class)
private class MalPullRobot(
    private val test: ComposeUiTest,
    ago: Duration?,
    syncing: Boolean = false,
) {
    var syncNowClicks = 0

    init {
        test.setContent {
            AppTheme {
                MalPullRow(
                    lastSyncedAt = ago?.let { now - it },
                    now = now,
                    syncing = syncing,
                    onSyncNowClick = { syncNowClicks++ },
                )
            }
        }
    }

    private fun syncNow() = test.onNodeWithTag(MalPullTags.SYNC_NOW)

    fun assertStatus(text: String) =
        apply { test.onNodeWithTag(MalPullTags.STATUS).assertIsDisplayed().assertTextEquals(text) }

    fun assertTextMissing(text: String) =
        apply { assertEquals(0, test.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().size) }

    fun assertSyncNowLabelShown(text: String) =
        apply { test.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed() }

    fun assertSyncNowEnabled() = apply { syncNow().assertIsDisplayed().assertIsEnabled().assertHasClickAction() }

    fun assertSyncNowDisabled() = apply { syncNow().assertIsDisplayed().assertIsNotEnabled() }

    fun assertSyncNowTouchHeight() = apply { syncNow().assertHeightIsAtLeast(48.dp) }

    fun assertSyncingAnnounced(label: String) =
        apply { test.onNodeWithContentDescription(str(Res.string.cd_in_progress, label)).assertIsNotEnabled() }

    fun clickSyncNow() = apply { syncNow().performClick() }

    fun assertSyncNowClicks(expected: Int) = apply { assertEquals(expected, syncNowClicks) }
}

private fun minutesText(count: Int) = plural(Res.plurals.settings_mal_last_synced_minutes, count, count)

private fun hoursText(count: Int) = plural(Res.plurals.settings_mal_last_synced_hours, count, count)

private fun daysText(count: Int) = plural(Res.plurals.settings_mal_last_synced_days, count, count)

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class MalPullRowTest {
    @Test
    fun tagsHaveStableNames() {
        assertEquals("mal_pull_status", MalPullTags.STATUS)
        assertEquals("mal_pull_sync_now", MalPullTags.SYNC_NOW)
    }

    @Test
    fun neverSyncedShowsTheNeverLabel() =
        runComposeUiTest {
            MalPullRobot(this, ago = null)
                .assertStatus(str(Res.string.settings_mal_last_synced_never))
        }

    @Test
    fun syncedAMomentAgoShowsJustNow() =
        runComposeUiTest {
            MalPullRobot(this, ago = 30.seconds)
                .assertStatus(str(Res.string.settings_mal_last_synced_just_now))
        }

    @Test
    fun syncedAtTheSameInstantShowsJustNow() =
        runComposeUiTest {
            MalPullRobot(this, ago = Duration.ZERO)
                .assertStatus(str(Res.string.settings_mal_last_synced_just_now))
        }

    @Test
    fun justUnderOneMinuteStillShowsJustNow() =
        runComposeUiTest {
            MalPullRobot(this, ago = 59.seconds)
                .assertStatus(str(Res.string.settings_mal_last_synced_just_now))
        }

    @Test
    fun oneMinuteAgoUsesTheSingularMinuteForm() =
        runComposeUiTest {
            MalPullRobot(this, ago = 1.minutes)
                .assertStatus(minutesText(1))
        }

    @Test
    fun severalMinutesAgoShowsTheMinuteCount() =
        runComposeUiTest {
            MalPullRobot(this, ago = 5.minutes + 40.seconds)
                .assertStatus(minutesText(5))
        }

    @Test
    fun justUnderOneHourShowsMinutes() =
        runComposeUiTest {
            MalPullRobot(this, ago = 59.minutes)
                .assertStatus(minutesText(59))
        }

    @Test
    fun oneHourAgoUsesTheSingularHourForm() =
        runComposeUiTest {
            MalPullRobot(this, ago = 1.hours)
                .assertStatus(hoursText(1))
        }

    @Test
    fun severalHoursAgoShowsTheHourCount() =
        runComposeUiTest {
            MalPullRobot(this, ago = 3.hours + 20.minutes)
                .assertStatus(hoursText(3))
        }

    @Test
    fun justUnderOneDayShowsHours() =
        runComposeUiTest {
            MalPullRobot(this, ago = 23.hours + 59.minutes)
                .assertStatus(hoursText(23))
        }

    @Test
    fun oneDayAgoUsesTheSingularDayForm() =
        runComposeUiTest {
            MalPullRobot(this, ago = 1.days)
                .assertStatus(daysText(1))
        }

    @Test
    fun severalDaysAgoShowsTheDayCount() =
        runComposeUiTest {
            MalPullRobot(this, ago = 4.days + 5.hours)
                .assertStatus(daysText(4))
        }

    @Test
    fun idleShowsSyncNowEnabled() =
        runComposeUiTest {
            MalPullRobot(this, ago = 5.minutes)
                .assertSyncNowLabelShown(str(Res.string.settings_mal_sync_now))
                .assertSyncNowEnabled()
                .assertTextMissing(str(Res.string.settings_mal_syncing))
        }

    @Test
    fun clickingSyncNowCallsTheCallback() =
        runComposeUiTest {
            MalPullRobot(this, ago = 5.minutes)
                .clickSyncNow()
                .assertSyncNowClicks(1)
        }

    @Test
    fun syncingDisablesTheButtonAndShowsTheSyncingLabel() =
        runComposeUiTest {
            MalPullRobot(this, ago = 5.minutes, syncing = true)
                .assertSyncingAnnounced(str(Res.string.settings_mal_syncing))
                .assertSyncNowDisabled()
                .assertTextMissing(str(Res.string.settings_mal_sync_now))
        }

    @Test
    fun syncingAnnouncesTheProgressToAccessibility() =
        runComposeUiTest {
            MalPullRobot(this, ago = null, syncing = true)
                .assertSyncingAnnounced(str(Res.string.settings_mal_syncing))
        }

    @Test
    fun clickingSyncNowWhileSyncingDoesNothing() =
        runComposeUiTest {
            MalPullRobot(this, ago = 5.minutes, syncing = true)
                .clickSyncNow()
                .assertSyncNowClicks(0)
        }

    @Test
    fun syncingKeepsTheLastSyncedText() =
        runComposeUiTest {
            MalPullRobot(this, ago = 2.hours, syncing = true)
                .assertStatus(hoursText(2))
        }

    @Test
    fun syncNowMeetsTheMinimumTouchHeight() =
        runComposeUiTest {
            MalPullRobot(this, ago = null).assertSyncNowTouchHeight()
        }
}
