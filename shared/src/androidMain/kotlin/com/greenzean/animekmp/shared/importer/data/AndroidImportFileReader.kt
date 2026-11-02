package com.greenzean.animekmp.shared.importer.data

import android.content.Context
import android.net.Uri
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.domain.ImportError
import com.greenzean.animekmp.shared.importer.domain.ImportFileReader
import com.greenzean.animekmp.shared.importer.domain.MAX_IMPORT_FILE_BYTES
import com.greenzean.animekmp.shared.importer.domain.readBytesAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.IOException

class AndroidImportFileReader(
    private val context: Context,
) : ImportFileReader {
    override suspend fun read(source: String): Result<ByteArray, ImportError> =
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(Uri.parse(source))
                val bytes = stream?.source()?.buffer()?.use { it.readBytesAtMost(MAX_IMPORT_FILE_BYTES) }
                if (bytes == null) Result.Error(ImportError.Unreadable) else Result.Success(bytes)
            } catch (_: IOException) {
                Result.Error(ImportError.Unreadable)
            } catch (_: SecurityException) {
                Result.Error(ImportError.Unreadable)
            }
        }
}
