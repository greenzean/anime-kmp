package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.components.AppCard
import com.greenzean.animekmp.composeapp.designsystem.components.AppListRow
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.settings_privacy_heading
import com.greenzean.animekmp.composeapp.generated.resources.settings_privacy_policy
import org.jetbrains.compose.resources.stringResource

const val PRIVACY_POLICY_URL = "https://greenzean.github.io/privacy/"

object PrivacyTags {
    const val ROW = "privacy_policy_row"
    const val LINK = "privacy_policy_link"
}

@Composable
fun PrivacySection(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier) {
        Text(
            stringResource(Res.string.settings_privacy_heading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs).semantics { heading() },
        )
        AppCard(modifier = Modifier.padding(top = MaterialTheme.spacing.md)) {
            AppListRow(
                title = stringResource(Res.string.settings_privacy_policy),
                onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
                modifier = Modifier.testTag(PrivacyTags.ROW),
            )
        }
    }
}

@Composable
fun PrivacyPolicyLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    AppButton(
        text = stringResource(Res.string.settings_privacy_policy),
        onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
        style = AppButtonStyle.Text,
        modifier = modifier.testTag(PrivacyTags.LINK),
    )
}
