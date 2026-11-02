package com.greenzean.animekmp.shared.importer.domain

import okio.BufferedSource

internal const val MAX_IMPORT_FILE_BYTES = 25L * 1024 * 1024
internal const val MAX_IMPORT_XML_BYTES = 40L * 1024 * 1024

internal fun BufferedSource.readBytesAtMost(limit: Long): ByteArray? {
    request(limit + 1)
    return if (buffer.size > limit) null else readByteArray()
}
