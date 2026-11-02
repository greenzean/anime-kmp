package com.greenzean.animekmp.shared.mal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.mal.domain.ObserveMalImportUseCase
import com.greenzean.animekmp.shared.mal.domain.StartMalImportUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val STOP_TIMEOUT_MILLIS = 5_000L

class MalImportViewModel(
    private val observeImport: ObserveMalImportUseCase,
    private val startImport: StartMalImportUseCase,
) : ViewModel() {
    val state: StateFlow<MalImportState> =
        observeImport()
            .map { MalImportState(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MalImportState())

    fun onAction(action: MalImportAction) {
        when (action) {
            MalImportAction.OnImportClick, MalImportAction.OnRetryClick -> startImport()
        }
    }
}
