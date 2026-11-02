package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MalImportCoordinator(
    private val import: ImportMalListUseCase,
    private val scope: CoroutineScope,
) {
    private val mutableStatus = MutableStateFlow<MalImportStatus>(MalImportStatus.Idle)
    val status: StateFlow<MalImportStatus> = mutableStatus.asStateFlow()

    fun start() {
        while (true) {
            val current = mutableStatus.value
            if (current is MalImportStatus.Importing) return
            if (mutableStatus.compareAndSet(current, MalImportStatus.Importing(0))) break
        }
        scope.launch {
            try {
                val result = import { done -> mutableStatus.value = MalImportStatus.Importing(done) }
                mutableStatus.value =
                    when (result) {
                        is Result.Success -> MalImportStatus.Done(result.data.added, result.data.conflicts)
                        is Result.Error -> MalImportStatus.Error
                    }
            } catch (e: CancellationException) {
                mutableStatus.value = MalImportStatus.Idle
                throw e
            }
        }
    }
}

class ObserveMalImportUseCase(
    private val coordinator: MalImportCoordinator,
) {
    operator fun invoke(): Flow<MalImportStatus> = coordinator.status
}

class StartMalImportUseCase(
    private val coordinator: MalImportCoordinator,
) {
    operator fun invoke() = coordinator.start()
}
