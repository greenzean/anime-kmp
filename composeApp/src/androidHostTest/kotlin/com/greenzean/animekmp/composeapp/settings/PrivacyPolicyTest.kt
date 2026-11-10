package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.AppTheme
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.settings_privacy_policy
import com.greenzean.animekmp.composeapp.mal.MalConnectionContent
import com.greenzean.animekmp.composeapp.str
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import com.greenzean.animekmp.shared.mal.presentation.MalAccountStatus
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingUriHandler : UriHandler {
    val opened = mutableListOf<String>()

    override fun openUri(uri: String) {
        opened += uri
    }
}

@Composable
private fun WithHandler(
    handler: UriHandler,
    content: @Composable () -> Unit,
) {
    AppTheme {
        CompositionLocalProvider(LocalUriHandler provides handler, content = content)
    }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class PrivacyPolicyTest {
    @Test
    fun settingsRowShowsLabelAndOpensPolicyUrl() =
        runComposeUiTest {
            val handler = RecordingUriHandler()
            setContent { WithHandler(handler) { PrivacySection() } }

            onNodeWithText(str(Res.string.settings_privacy_policy)).assertIsDisplayed()
            onNodeWithTag(PrivacyTags.ROW).assertHasClickAction().performClick()

            assertEquals(listOf(PRIVACY_POLICY_URL), handler.opened)
        }

    @Test
    fun consentShowsPolicyLinkThatOpensPolicyUrl() =
        runComposeUiTest {
            val handler = RecordingUriHandler()
            setContent {
                WithHandler(handler) {
                    MalConnectionContent(state = MalAccountState(MalAccountStatus.NeedsConsent), onAction = {})
                }
            }

            onNodeWithTag(PrivacyTags.LINK).assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()

            assertEquals(listOf(PRIVACY_POLICY_URL), handler.opened)
        }
}
