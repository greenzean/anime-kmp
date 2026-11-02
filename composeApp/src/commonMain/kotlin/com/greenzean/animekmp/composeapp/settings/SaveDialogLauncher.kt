package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.Composable

@Composable
expect fun rememberSaveDialogLauncher(onResult: (String?) -> Unit): (String) -> Unit
