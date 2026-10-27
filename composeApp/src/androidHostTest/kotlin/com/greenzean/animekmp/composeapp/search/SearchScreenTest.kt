package com.greenzean.animekmp.composeapp.search

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_retry
import com.greenzean.animekmp.composeapp.generated.resources.action_try_again
import com.greenzean.animekmp.composeapp.generated.resources.cd_add_to_library
import com.greenzean.animekmp.composeapp.generated.resources.cd_searching
import com.greenzean.animekmp.composeapp.generated.resources.error_mal_unreachable
import com.greenzean.animekmp.composeapp.generated.resources.label_in_library
import com.greenzean.animekmp.composeapp.generated.resources.offline_title
import com.greenzean.animekmp.composeapp.generated.resources.search_empty_message
import com.greenzean.animekmp.composeapp.generated.resources.search_empty_title
import com.greenzean.animekmp.composeapp.generated.resources.search_idle_title
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.search.presentation.SearchAction
import com.greenzean.animekmp.shared.search.presentation.SearchResultUi
import com.greenzean.animekmp.shared.search.presentation.SearchState
import com.greenzean.animekmp.shared.search.presentation.SearchStatus
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

private val results =
    SearchState(
        query = "frieren",
        results =
            listOf(
                SearchResultUi(52991, "Frieren", null, inLibrary = false),
                SearchResultUi(9253, "Steins;Gate", null, inLibrary = true),
            ),
        status = SearchStatus.Results,
    )

@OptIn(ExperimentalTestApi::class)
private class SearchRobot(
    private val test: ComposeUiTest,
    state: SearchState,
) {
    val actions = mutableListOf<SearchAction>()

    init {
        test.setContent { AppTheme { SearchScreen(state = state, onAction = { actions += it }) } }
    }

    fun assertTextShown(text: String) = apply { test.onNodeWithText(text).assertIsDisplayed() }

    fun assertDescriptionShown(description: String) =
        apply {
            test.onNodeWithContentDescription(description).assertIsDisplayed()
        }

    fun assertTextMissing(text: String) = apply { test.onNodeWithText(text).assertDoesNotExist() }

    fun assertDescriptionMissing(description: String) =
        apply {
            test.onNodeWithContentDescription(description).assertDoesNotExist()
        }

    fun clickText(text: String) = apply { test.onNodeWithText(text).performClick() }

    fun clickDescription(description: String) = apply { test.onNodeWithContentDescription(description).performClick() }

    fun typeQuery(text: String) = apply { test.onNode(hasSetTextAction()).performTextReplacement(text) }

    fun assertDescriptionIsTouchTarget(description: String) =
        apply {
            test
                .onNodeWithContentDescription(description)
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
        }

    fun assertActions(vararg expected: SearchAction) = apply { assertEquals(expected.toList(), actions) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchScreenTest {
    @Test
    fun idleInvitesTheUserToType() =
        runComposeUiTest {
            SearchRobot(this, SearchState()).assertTextShown(str(Res.string.search_idle_title))
        }

    @Test
    fun loadingShowsAProgressIndicatorWithALabel() =
        runComposeUiTest {
            SearchRobot(this, SearchState(query = "frieren", status = SearchStatus.Loading))
                .assertDescriptionShown(str(Res.string.cd_searching))
        }

    @Test
    fun resultsShowEveryTitle() =
        runComposeUiTest {
            SearchRobot(this, results).assertTextShown("Frieren").assertTextShown("Steins;Gate")
        }

    @Test
    fun emptyNamesTheQuery() =
        runComposeUiTest {
            SearchRobot(this, SearchState(query = " zzzz ", status = SearchStatus.Empty))
                .assertTextShown(str(Res.string.search_empty_title))
                .assertTextShown(str(Res.string.search_empty_message, "zzzz"))
        }

    @Test
    fun offlineRetrySendsRetry() =
        runComposeUiTest {
            SearchRobot(this, SearchState(query = "frieren", status = SearchStatus.Offline))
                .assertTextShown(str(Res.string.offline_title))
                .clickText(str(Res.string.action_try_again))
                .assertActions(SearchAction.OnRetryClick)
        }

    @Test
    fun errorRetrySendsRetry() =
        runComposeUiTest {
            SearchRobot(this, SearchState(query = "frieren", status = SearchStatus.Error))
                .assertTextShown(str(Res.string.error_mal_unreachable))
                .clickText(str(Res.string.action_retry))
                .assertActions(SearchAction.OnRetryClick)
        }

    @Test
    fun typingSendsTheQueryChange() =
        runComposeUiTest {
            SearchRobot(this, SearchState()).typeQuery("naruto").assertActions(SearchAction.OnQueryChange("naruto"))
        }

    @Test
    fun tappingAResultSendsResultClickAndNotAdd() =
        runComposeUiTest {
            SearchRobot(this, results)
                .clickText("Frieren")
                .assertActions(SearchAction.OnResultClick(52991))
        }

    @Test
    fun addSendsAddForThatResultOnly() =
        runComposeUiTest {
            SearchRobot(this, results)
                .clickDescription(str(Res.string.cd_add_to_library, "Frieren"))
                .assertActions(SearchAction.OnAddClick(52991))
        }

    @Test
    fun resultsInTheLibraryShowInLibraryInsteadOfAdd() =
        runComposeUiTest {
            SearchRobot(this, results)
                .assertTextShown(str(Res.string.label_in_library))
                .assertDescriptionMissing(str(Res.string.cd_add_to_library, "Steins;Gate"))
                .assertDescriptionShown(str(Res.string.cd_add_to_library, "Frieren"))
        }

    @Test
    fun addButtonMeetsTheMinimumTouchTarget() =
        runComposeUiTest {
            SearchRobot(this, results).assertDescriptionIsTouchTarget(str(Res.string.cd_add_to_library, "Frieren"))
        }
}
