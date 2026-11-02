package com.greenzean.animekmp.composeapp.settings

import androidx.compose.runtime.Composable

@Composable
expect fun rememberOpenDialogLauncher(onResult: (String?) -> Unit): () -> Unit
