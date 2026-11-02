package com.greenzean.animekmp.shared.export.domain

import com.greenzean.animekmp.shared.core.domain.Error

enum class ExportError : Error {
    EmptyLibrary,
    WriteFailed,
}

data class ExportDocument(
    val fileName: String,
    val content: String,
    val count: Int,
)
