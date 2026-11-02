package com.greenzean.animekmp.composeapp.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.greenzean.animekmp.composeapp.designsystem.components.AnimeCover
import com.greenzean.animekmp.composeapp.designsystem.components.AppButton
import com.greenzean.animekmp.composeapp.designsystem.components.AppButtonStyle
import com.greenzean.animekmp.composeapp.designsystem.components.AppListRow
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.action_add
import com.greenzean.animekmp.composeapp.generated.resources.cd_add_to_library
import com.greenzean.animekmp.composeapp.generated.resources.label_in_library
import com.greenzean.animekmp.shared.search.presentation.SearchResultUi
import org.jetbrains.compose.resources.stringResource

private const val LOAD_MORE_DISTANCE = 3

@Composable
internal fun SearchResultRow(
    result: SearchResultUi,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    addModifier: Modifier = Modifier,
) {
    AppListRow(
        title = result.title,
        onClick = onClick,
        leading = { AnimeCover(result.imageUrl) },
        trailing = { AddAction(result = result, onAdd = onAdd, modifier = addModifier) },
        modifier = modifier,
    )
}

@Composable
private fun AddAction(
    result: SearchResultUi,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (result.inLibrary) {
        Text(
            stringResource(Res.string.label_in_library),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        val addDescription = stringResource(Res.string.cd_add_to_library, result.title)
        AppButton(
            text = stringResource(Res.string.action_add),
            onClick = onAdd,
            style = AppButtonStyle.Tonal,
            modifier = modifier.semantics { contentDescription = addDescription },
        )
    }
}

@Composable
internal fun LoadMoreEffect(
    listState: LazyListState,
    itemCount: Int,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, enabled) {
        if (enabled) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            }.collect { last ->
                if (last >= itemCount - LOAD_MORE_DISTANCE) currentOnLoadMore()
            }
        }
    }
}
