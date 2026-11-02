package com.greenzean.animekmp.shared.importer.domain

import com.greenzean.animekmp.shared.core.domain.Result

interface ImportFileReader {
    suspend fun read(source: String): Result<ByteArray, ImportError>
}
