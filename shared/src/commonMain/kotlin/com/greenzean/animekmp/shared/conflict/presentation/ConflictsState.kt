package com.greenzean.animekmp.shared.conflict.presentation

import com.greenzean.animekmp.shared.conflict.domain.EntryValues

data class ConflictUi(
    val malId: Int,
    val title: String,
    val app: EntryValues,
    val mal: EntryValues,
    val imageUrl: String? = null,
)

data class ConflictsState(
    val items: List<ConflictUi> = emptyList(),
    val isLoading: Boolean = true,
    val showKeepAllMalDialog: Boolean = false,
)

sealed interface ConflictsAction {
    data class OnKeepAppClick(
        val malId: Int,
    ) : ConflictsAction

    data class OnKeepMalClick(
        val malId: Int,
    ) : ConflictsAction

    data object OnKeepAllAppClick : ConflictsAction

    data object OnKeepAllMalClick : ConflictsAction

    data object OnKeepAllMalConfirm : ConflictsAction

    data object OnDismissDialog : ConflictsAction
}
