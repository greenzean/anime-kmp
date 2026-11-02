package com.greenzean.animekmp.shared.conflict.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.conflict.domain.Conflict
import com.greenzean.animekmp.shared.conflict.domain.ConflictSide
import com.greenzean.animekmp.shared.conflict.domain.ObserveConflictsUseCase
import com.greenzean.animekmp.shared.conflict.domain.ResolveAllConflictsUseCase
import com.greenzean.animekmp.shared.conflict.domain.ResolveConflictUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConflictsViewModel(
    observeConflicts: ObserveConflictsUseCase,
    private val resolveConflict: ResolveConflictUseCase,
    private val resolveAllConflicts: ResolveAllConflictsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ConflictsState())
    val state: StateFlow<ConflictsState> = _state.asStateFlow()

    init {
        observeConflicts()
            .onEach { list ->
                _state.update { it.copy(items = list.map { conflict -> conflict.toConflictUi() }, isLoading = false) }
            }.launchIn(viewModelScope)
    }

    fun onAction(action: ConflictsAction) {
        when (action) {
            is ConflictsAction.OnKeepAppClick -> {
                viewModelScope.launch { resolveConflict(action.malId, ConflictSide.App) }
            }

            is ConflictsAction.OnKeepMalClick -> {
                viewModelScope.launch { resolveConflict(action.malId, ConflictSide.Other) }
            }

            ConflictsAction.OnKeepAllAppClick -> {
                viewModelScope.launch { resolveAllConflicts(ConflictSide.App) }
            }

            ConflictsAction.OnKeepAllMalClick -> {
                _state.update { it.copy(showKeepAllMalDialog = true) }
            }

            ConflictsAction.OnKeepAllMalConfirm -> {
                _state.update { it.copy(showKeepAllMalDialog = false) }
                viewModelScope.launch { resolveAllConflicts(ConflictSide.Other) }
            }

            ConflictsAction.OnDismissDialog -> {
                _state.update { it.copy(showKeepAllMalDialog = false) }
            }
        }
    }
}

private fun Conflict.toConflictUi(): ConflictUi =
    ConflictUi(malId = malId, title = title, app = app, mal = other, imageUrl = imageUrl)
