package com.greenzean.animekmp.composeapp.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

private val ImportMimeTypes =
    arrayOf("text/xml", "application/xml", "application/gzip", "application/x-gzip", "application/octet-stream")

@Composable
actual fun rememberOpenDialogLauncher(onResult: (String?) -> Unit): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onResult(uri?.toString())
        }
    return { launcher.launch(ImportMimeTypes) }
}
