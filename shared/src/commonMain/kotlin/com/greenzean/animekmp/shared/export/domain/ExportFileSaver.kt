package com.greenzean.animekmp.shared.export.domain

import com.greenzean.animekmp.shared.core.domain.EmptyResult

interface ExportFileSaver {
    suspend fun save(
        destination: String,
        content: String,
    ): EmptyResult<ExportError>
}
