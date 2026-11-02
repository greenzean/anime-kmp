package com.greenzean.animekmp.composeapp.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
actual fun rememberSaveDialogLauncher(onResult: (String?) -> Unit): (String) -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri ->
            onResult(uri?.toString())
        }
    return { fileName -> launcher.launch(fileName) }
}
