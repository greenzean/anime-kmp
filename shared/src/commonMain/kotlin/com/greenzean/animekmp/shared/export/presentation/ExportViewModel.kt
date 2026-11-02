package com.greenzean.animekmp.shared.export.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.export.domain.ExportDocument
import com.greenzean.animekmp.shared.export.domain.ExportLibraryToMalUseCase
import com.greenzean.animekmp.shared.export.domain.SaveExportFileUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExportViewModel(
    private val exportLibraryToMal: ExportLibraryToMalUseCase,
    private val saveExportFile: SaveExportFileUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private val _events = Channel<ExportEvent>()
    val events: Flow<ExportEvent> = _events.receiveAsFlow()

    private var pending: ExportDocument? = null

    fun onAction(action: ExportAction) {
        when (action) {
            ExportAction.OnExportClick -> export()
            is ExportAction.OnFileChosen -> fileChosen(action.destination)
        }
    }

    private fun export() {
        viewModelScope.launch {
            _state.update { it.copy(status = ExportStatus.Exporting) }
            when (val result = exportLibraryToMal()) {
                is Result.Success -> {
                    pending = result.data
                    _events.send(ExportEvent.LaunchSaveDialog(result.data.fileName))
                }

                is Result.Error -> {
                    _state.update { it.copy(status = ExportStatus.Error(result.error)) }
                }
            }
        }
    }

    private fun fileChosen(destination: String?) {
        val document = pending
        if (destination == null) {
            pending = null
            _state.update { it.copy(status = ExportStatus.Idle) }
            return
        }
        if (document == null) return
        pending = null
        viewModelScope.launch {
            when (val result = saveExportFile(destination, document.content)) {
                is Result.Success -> _state.update { it.copy(status = ExportStatus.Done(document.count)) }
                is Result.Error -> _state.update { it.copy(status = ExportStatus.Error(result.error)) }
            }
        }
    }
}
