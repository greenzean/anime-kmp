package com.greenzean.animekmp.shared.export.data

import android.content.Context
import android.net.Uri
import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.export.domain.ExportError
import com.greenzean.animekmp.shared.export.domain.ExportFileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class AndroidExportFileSaver(
    private val context: Context,
) : ExportFileSaver {
    override suspend fun save(
        destination: String,
        content: String,
    ): EmptyResult<ExportError> =
        withContext(Dispatchers.IO) {
            runCatching {
                val stream = context.contentResolver.openOutputStream(Uri.parse(destination))
                stream?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                stream != null
            }.onFailure {
                if (it !is IOException && it !is SecurityException) throw it
            }.fold(
                onSuccess = { written -> if (written) Result.Success(Unit) else Result.Error(ExportError.WriteFailed) },
                onFailure = { Result.Error(ExportError.WriteFailed) },
            )
        }
}
