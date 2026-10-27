package com.greenzean.animekmp.composeapp.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

val MinTouchTarget: Dp = 48.dp

private val AppSpacing = Spacing()

val MaterialTheme.spacing: Spacing
    get() = AppSpacing
