package com.greenzean.animekmp.shared.export.domain

import com.greenzean.animekmp.shared.core.domain.EmptyResult

class SaveExportFileUseCase(
    private val saver: ExportFileSaver,
) {
    suspend operator fun invoke(
        destination: String,
        content: String,
    ): EmptyResult<ExportError> = saver.save(destination, content)
}
