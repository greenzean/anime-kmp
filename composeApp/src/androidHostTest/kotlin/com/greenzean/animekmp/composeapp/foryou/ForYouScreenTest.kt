package com.greenzean.animekmp.composeapp.foryou

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_try_again
import com.greenzean.animekmp.composeapp.generated.resources.cd_add_to_library
import com.greenzean.animekmp.composeapp.generated.resources.cd_back
import com.greenzean.animekmp.composeapp.generated.resources.for_you_all_added_title
import com.greenzean.animekmp.composeapp.generated.resources.for_you_empty_message
import com.greenzean.animekmp.composeapp.generated.resources.for_you_empty_title
import com.greenzean.animekmp.composeapp.generated.resources.for_you_error_message
import com.greenzean.animekmp.composeapp.generated.resources.for_you_error_title
import com.greenzean.animekmp.composeapp.generated.resources.for_you_list
import com.greenzean.animekmp.composeapp.generated.resources.for_you_loading
import com.greenzean.animekmp.composeapp.generated.resources.for_you_not_connected_message
import com.greenzean.animekmp.composeapp.generated.resources.for_you_not_connected_title
import com.greenzean.animekmp.composeapp.generated.resources.for_you_offline_message
import com.greenzean.animekmp.composeapp.generated.resources.for_you_offline_title
import com.greenzean.animekmp.composeapp.generated.resources.for_you_title
import com.greenzean.animekmp.composeapp.generated.resources.mal_connect
import com.greenzean.animekmp.composeapp.generated.resources.mal_connecting
import com.greenzean.animekmp.composeapp.generated.resources.mal_consent_body
import com.greenzean.animekmp.composeapp.generated.resources.mal_error_no_internet
import com.greenzean.animekmp.composeapp.mal.MalAccountTags
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.mal.presentation.MalAccountAction
import com.greenzean.animekmp.shared.mal.presentation.MalAccountError
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus
import com.greenzean.animekmp.shared.search.presentation.SearchResultUi
import com.greenzean.animekmp.shared.suggestions.presentation.SuggestionsAction
import com.greenzean.animekmp.shared.suggestions.presentation.SuggestionsState
import com.greenzean.animekmp.shared.suggestions.presentation.SuggestionsStatus
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

private val loaded =
    SuggestionsState(
        items =
            listOf(
                SearchResultUi(52991, "Frieren", null, inLibrary = false),
                SearchResultUi(9253, "Steins;Gate", null, inLibrary = false),
            ),
        status = SuggestionsStatus.Loaded,
    )

private fun stateOf(status: SuggestionsStatus) = SuggestionsState(status = status)

@OptIn(ExperimentalTestApi::class)
private class ForYouRobot(
    private val test: ComposeUiTest,
    state: SuggestionsState,
    malState: MalAccountState = MalAccountState(),
) {
    val actions = mutableListOf<SuggestionsAction>()
    val malActions = mutableListOf<MalAccountAction>()

    init {
        test.setContent {
            AppTheme {
                ForYouScreen(
                    state = state,
                    malState = malState,
                    onAction = { actions += it },
                    onMalAction = { malActions += it },
                )
            }
        }
    }

    private fun tagCount(tag: String) = test.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    fun assertTextShown(text: String) = apply { test.onNodeWithText(text).assertIsDisplayed() }

    fun assertTextMissing(text: String) =
        apply { assertEquals(0, test.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size) }

    fun assertTagShown(tag: String) = apply { test.onNodeWithTag(tag).assertIsDisplayed() }

    fun assertTagMissing(tag: String) = apply { assertEquals(0, tagCount(tag)) }

    fun assertDescriptionShown(description: String) =
        apply { test.onNodeWithContentDescription(description).assertIsDisplayed() }

    fun assertDescriptionMissing(description: String) =
        apply {
            assertEquals(0, test.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().size)
        }

    fun assertTitleIsHeading() =
        apply {
            test
                .onNodeWithText(str(Res.string.for_you_title))
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }

    fun assertNoBackButton() = apply { assertDescriptionMissing(str(Res.string.cd_back)) }

    fun assertTouchTarget(tag: String) =
        apply { test.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp) }

    fun clickTag(tag: String) = apply { test.onNodeWithTag(tag).performClick() }

    fun clickText(text: String) = apply { test.onNodeWithText(text).performClick() }

    fun clickDescription(description: String) = apply { test.onNodeWithContentDescription(description).performClick() }

    fun assertActions(vararg expected: SuggestionsAction) = apply { assertEquals(expected.toList(), actions) }

    fun assertMalActions(vararg expected: MalAccountAction) = apply { assertEquals(expected.toList(), malActions) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class ForYouScreenTest {
    @Test
    fun titleIsAHeadingWithoutBackButton() =
        runComposeUiTest {
            ForYouRobot(this, loaded)
                .assertTextShown(str(Res.string.for_you_title))
                .assertTitleIsHeading()
                .assertNoBackButton()
        }

    @Test
    fun notConnectedExplainsWhyAndOffersTheConnectFlow() =
        runComposeUiTest {
            ForYouRobot(this, stateOf(SuggestionsStatus.NotConnected), MalAccountState(MalAccountStatus.NotConnected))
                .assertTextShown(str(Res.string.for_you_not_connected_title))
                .assertTextShown(str(Res.string.for_you_not_connected_message))
                .assertTagShown(MalAccountTags.CONNECT)
                .assertTagMissing(ForYouTags.LIST)
                .assertTagMissing(ForYouTags.LOADING)
        }

    @Test
    fun notConnectedConnectSendsConnectClickToTheMalViewModel() =
        runComposeUiTest {
            ForYouRobot(this, stateOf(SuggestionsStatus.NotConnected), MalAccountState(MalAccountStatus.NotConnected))
                .assertTouchTarget(MalAccountTags.CONNECT)
                .clickTag(MalAccountTags.CONNECT)
                .assertMalActions(MalAccountAction.OnConnectClick)
                .assertActions()
        }

    @Test
    fun notConnectedAsksForConsentFirst() =
        runComposeUiTest {
            ForYouRobot(this, stateOf(SuggestionsStatus.NotConnected), MalAccountState(MalAccountStatus.NeedsConsent))
                .assertTextShown(str(Res.string.mal_consent_body))
                .assertTagShown(MalAccountTags.CONSENT_ACCEPT)
                .assertTagMissing(MalAccountTags.CONNECT)
                .clickTag(MalAccountTags.CONSENT_ACCEPT)
                .assertMalActions(MalAccountAction.OnConsentAccepted)
        }

    @Test
    fun notConnectedShowsProgressWhileConnecting() =
        runComposeUiTest {
            ForYouRobot(this, stateOf(SuggestionsStatus.NotConnected), MalAccountState(MalAccountStatus.Connecting))
                .assertTextShown(str(Res.string.mal_connecting))
                .assertTagMissing(MalAccountTags.CONNECT)
        }

    @Test
    fun notConnectedShowsTheConnectErrorAndRetries() =
        runComposeUiTest {
            ForYouRobot(
                this,
                stateOf(SuggestionsStatus.NotConnected),
                MalAccountState(MalAccountStatus.Error(MalAccountError.NoInternet)),
            ).assertTagShown(MalAccountTags.ERROR)
                .assertTextShown(str(Res.string.mal_error_no_internet))
                .assertTouchTarget(MalAccountTags.RETRY)
                .clickTag(MalAccountTags.RETRY)
                .assertMalActions(MalAccountAction.OnConnectClick)
        }

    @Test
    fun connectLabelIsReadable() =
        runComposeUiTest {
            ForYouRobot(this, stateOf(SuggestionsStatus.NotConnected), MalAccountState(MalAccountStatus.NotConnected))
                .assertTextShown(str(Res.string.mal_connect))
        }

    @Test
    fun loadingShowsAProgressIndicatorWithALabel() =
        runComposeUiTest {
            ForYouRobot(this, stateOf(SuggestionsStatus.Loading))
                .assertTagShown(ForYouTags.LOADING)
                .assertDescriptionShown(str(Res.string.for_you_loading))
                .assertTagMissing(ForYouTags.LIST)
                .assertTagMissing(MalAccountTags.CONNECT)
        }

    @Test
    fun loadedShowsEveryTitleInALabelledList() =
        runComposeUiTest {
            ForYouRobot(this, loaded)
                .assertTagShown(ForYouTags.LIST)
                .assertDescriptionShown(str(Res.string.for_you_list))
                .assertTextShown("Frieren")
                .assertTextShown("Steins;Gate")
                .assertTagShown(ForYouTags.item(52991))
                .assertTagShown(ForYouTags.item(9253))
                .assertTagMissing(ForYouTags.LOADING)
                .assertTagMissing(MalAccountTags.CONNECT)
        }

    @Test
    fun loadedWithEveryItemInTheLibraryExplainsThereIsNothingNew() =
        runComposeUiTest {
            ForYouRobot(this, SuggestionsState(status = SuggestionsStatus.Loaded))
                .assertTextShown(str(Res.string.for_you_all_added_title))
                .assertTagMissing(ForYouTags.LIST)
        }

    @Test
    fun emptyExplainsThatMalNeedsRatedAnime() =
        runComposeUiTest {
            ForYouRobot(this, stateOf(SuggestionsStatus.Empty))
                .assertTextShown(str(Res.string.for_you_empty_title))
                .assertTextShown(str(Res.string.for_you_empty_message))
                .assertTagMissing(ForYouTags.LIST)
                .assertTagMissing(ForYouTags.RETRY)
        }

    @Test
    fun offlineOffersRetry() =
        runComposeUiTest {
            ForYouRobot(this, stateOf(SuggestionsStatus.Offline))
                .assertTextShown(str(Res.string.for_you_offline_title))
                .assertTextShown(str(Res.string.for_you_offline_message))
                .assertTagShown(ForYouTags.RETRY)
                .assertTextShown(str(Res.string.action_try_again))
        }

    @Test
    fun offlineRetrySendsRetry() =
        runComposeUiTest {
            ForYouRobot(this, stateOf(SuggestionsStatus.Offline))
                .assertTouchTarget(ForYouTags.RETRY)
                .clickTag(ForYouTags.RETRY)
                .assertActions(SuggestionsAction.OnRetryClick)
        }

    @Test
    fun errorOffersRetry() =
        runComposeUiTest {
            ForYouRobot(this, stateOf(SuggestionsStatus.Error))
                .assertTextShown(str(Res.string.for_you_error_title))
                .assertTextShown(str(Res.string.for_you_error_message))
                .assertTagShown(ForYouTags.RETRY)
        }

    @Test
    fun errorRetrySendsRetry() =
        runComposeUiTest {
            ForYouRobot(this, stateOf(SuggestionsStatus.Error))
                .clickText(str(Res.string.action_try_again))
                .assertActions(SuggestionsAction.OnRetryClick)
        }

    @Test
    fun tappingACardSendsItemClickAndNotAdd() =
        runComposeUiTest {
            ForYouRobot(this, loaded)
                .clickTag(ForYouTags.item(52991))
                .assertActions(SuggestionsAction.OnItemClick(52991))
        }

    @Test
    fun tappingTheTitleSendsItemClick() =
        runComposeUiTest {
            ForYouRobot(this, loaded)
                .clickText("Steins;Gate")
                .assertActions(SuggestionsAction.OnItemClick(9253))
        }

    @Test
    fun addSendsAddForThatCardOnly() =
        runComposeUiTest {
            ForYouRobot(this, loaded)
                .clickTag(ForYouTags.add(52991))
                .assertActions(SuggestionsAction.OnAddClick(52991))
        }

    @Test
    fun addButtonsAreLabelledPerTitle() =
        runComposeUiTest {
            ForYouRobot(this, loaded)
                .assertDescriptionShown(str(Res.string.cd_add_to_library, "Frieren"))
                .assertDescriptionShown(str(Res.string.cd_add_to_library, "Steins;Gate"))
                .clickDescription(str(Res.string.cd_add_to_library, "Steins;Gate"))
                .assertActions(SuggestionsAction.OnAddClick(9253))
        }

    @Test
    fun addButtonMeetsTheMinimumTouchTarget() =
        runComposeUiTest {
            ForYouRobot(this, loaded).assertTouchTarget(ForYouTags.add(52991))
        }

    @Test
    fun reachingTheEndWithMoreAvailableSendsLoadMore() =
        runComposeUiTest {
            val robot = ForYouRobot(this, loaded.copy(canLoadMore = true))
            waitForIdle()

            assertEquals(true, SuggestionsAction.OnLoadMore in robot.actions)
        }

    @Test
    fun withoutMoreAvailableLoadMoreIsNotSent() =
        runComposeUiTest {
            val robot = ForYouRobot(this, loaded.copy(canLoadMore = false))
            waitForIdle()

            robot.assertActions()
        }
}
