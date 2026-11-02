package com.greenzean.animekmp.shared.importer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.conflict.domain.ObserveConflictCountUseCase
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.domain.FillImportedImagesUseCase
import com.greenzean.animekmp.shared.importer.domain.ImportMalFileUseCase
import com.greenzean.animekmp.shared.importer.domain.ReadImportFileUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImportFileViewModel(
    private val readImportFile: ReadImportFileUseCase,
    private val importMalFile: ImportMalFileUseCase,
    private val fillImportedImages: FillImportedImagesUseCase,
    observeConflictCount: ObserveConflictCountUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportFileState())
    val state: StateFlow<ImportFileState> = _state.asStateFlow()

    private val _events = Channel<ImportFileEvent>()
    val events: Flow<ImportFileEvent> = _events.receiveAsFlow()

    init {
        observeConflictCount()
            .onEach { count -> _state.update { it.copy(conflictCount = count) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ImportFileAction) {
        when (action) {
            ImportFileAction.OnImportClick -> {
                importClick()
            }

            is ImportFileAction.OnFileChosen -> {
                fileChosen(action.source)
            }

            ImportFileAction.OnConflictsClick -> {
                viewModelScope.launch { _events.send(ImportFileEvent.NavigateToConflicts) }
            }
        }
    }

    private fun importClick() {
        if (_state.value.status is ImportStatus.Importing) return
        viewModelScope.launch { _events.send(ImportFileEvent.LaunchOpenDialog) }
    }

    private fun fileChosen(source: String?) {
        if (source == null) {
            _state.update { it.copy(status = ImportStatus.Idle) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(status = ImportStatus.Importing(0f)) }
            when (val read = readImportFile(source)) {
                is Result.Error -> {
                    _state.update { it.copy(status = ImportStatus.Error(read.error)) }
                }

                is Result.Success -> {
                    val summary =
                        importMalFile(read.data) { progress ->
                            _state.update { it.copy(status = ImportStatus.Importing(progress)) }
                        }
                    _state.update {
                        it.copy(status = ImportStatus.Done(summary.added, summary.conflicts, summary.invalid))
                    }
                    fillImportedImages()
                }
            }
        }
    }
}
