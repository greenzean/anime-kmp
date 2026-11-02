package com.greenzean.animekmp.composeapp.conflicts

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.conflict.domain.EntryValues
import com.greenzean.animekmp.shared.conflict.presentation.ConflictUi
import com.greenzean.animekmp.shared.conflict.presentation.ConflictsState
import com.greenzean.animekmp.shared.library.domain.WatchStatus

private const val BEBOP_ID = 1
private const val FRIEREN_ID = 52991
private const val BEBOP_EPISODES = 26
private const val FRIEREN_PROGRESS = 12
private const val FRIEREN_SCORE = 9
private const val BEBOP_SCORE = 8

private val previewItems =
    listOf(
        ConflictUi(
            malId = BEBOP_ID,
            title = "Cowboy Bebop",
            app = EntryValues(WatchStatus.WATCHING, FRIEREN_PROGRESS, null, removed = false),
            mal = EntryValues(WatchStatus.COMPLETED, BEBOP_EPISODES, BEBOP_SCORE, removed = false),
        ),
        ConflictUi(
            malId = FRIEREN_ID,
            title = "Sousou no Frieren",
            app = EntryValues(WatchStatus.COMPLETED, BEBOP_EPISODES, FRIEREN_SCORE, removed = false),
            mal = EntryValues(WatchStatus.PLAN_TO_WATCH, 0, null, removed = false),
        ),
    )

private val previewRemoved =
    listOf(
        ConflictUi(
            malId = BEBOP_ID,
            title = "Cowboy Bebop",
            app = EntryValues(WatchStatus.WATCHING, 0, null, removed = true),
            mal = EntryValues(WatchStatus.COMPLETED, BEBOP_EPISODES, BEBOP_SCORE, removed = false),
        ),
    )

@Preview
@Composable
private fun ConflictsListLightPreview() = ConflictsPreview(ConflictsState(previewItems, isLoading = false), false)

@Preview
@Composable
private fun ConflictsListDarkPreview() = ConflictsPreview(ConflictsState(previewItems, isLoading = false), true)

@Preview
@Composable
private fun ConflictsRemovedLightPreview() = ConflictsPreview(ConflictsState(previewRemoved, isLoading = false), false)

@Preview
@Composable
private fun ConflictsRemovedDarkPreview() = ConflictsPreview(ConflictsState(previewRemoved, isLoading = false), true)

@Preview
@Composable
private fun ConflictsEmptyLightPreview() = ConflictsPreview(ConflictsState(isLoading = false), false)

@Preview
@Composable
private fun ConflictsEmptyDarkPreview() = ConflictsPreview(ConflictsState(isLoading = false), true)

@Preview
@Composable
private fun ConflictsDialogLightPreview() =
    ConflictsPreview(ConflictsState(previewItems, isLoading = false, showKeepAllMalDialog = true), false)

@Preview
@Composable
private fun ConflictsDialogDarkPreview() =
    ConflictsPreview(ConflictsState(previewItems, isLoading = false, showKeepAllMalDialog = true), true)

@Composable
private fun ConflictsPreview(
    state: ConflictsState,
    darkTheme: Boolean,
) {
    ThemedPreview(darkTheme) { ConflictsScreen(state = state, onAction = {}, onNavigateBack = {}) }
}
