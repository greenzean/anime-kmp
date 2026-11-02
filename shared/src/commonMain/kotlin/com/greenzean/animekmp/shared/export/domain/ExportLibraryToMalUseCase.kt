package com.greenzean.animekmp.shared.export.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

class ExportLibraryToMalUseCase(
    private val observeLibrary: ObserveLibraryUseCase,
    private val writer: MalXmlWriter,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Result<ExportDocument, ExportError> {
        val entries = observeLibrary().first()
        if (entries.isEmpty()) return Result.Error(ExportError.EmptyLibrary)
        return Result.Success(
            ExportDocument(
                fileName = ExportFileName.forInstant(clock.now()),
                content = writer.write(entries),
                count = entries.size,
            ),
        )
    }
}
