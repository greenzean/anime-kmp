package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.greenzean.animekmp.composeapp.designsystem.components.AppCard
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_title
import com.greenzean.animekmp.composeapp.mal.MalConnectionContent
import com.greenzean.animekmp.shared.mal.presentation.MalAccountAction
import com.greenzean.animekmp.shared.mal.presentation.MalAccountState
import org.jetbrains.compose.resources.stringResource

@Composable
fun MalAccountSection(
    state: MalAccountState,
    onAction: (MalAccountAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            stringResource(Res.string.settings_mal_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs).semantics { heading() },
        )
        AppCard(modifier = Modifier.padding(top = MaterialTheme.spacing.md)) {
            MalConnectionContent(state = state, onAction = onAction)
        }
    }
}
