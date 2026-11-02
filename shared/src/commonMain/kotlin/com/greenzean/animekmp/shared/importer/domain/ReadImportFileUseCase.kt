package com.greenzean.animekmp.shared.importer.domain

import com.greenzean.animekmp.shared.core.domain.Result

class ReadImportFileUseCase(
    private val reader: ImportFileReader,
    private val parser: MalXmlParser,
) {
    suspend operator fun invoke(source: String): Result<MalExportFile, ImportError> =
        when (val read = reader.read(source)) {
            is Result.Success -> parser.parse(read.data)
            is Result.Error -> Result.Error(read.error)
        }
}
