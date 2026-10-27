package com.greenzean.animekmp.composeapp.designsystem.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CoverSize(
    val width: Dp,
    val height: Dp,
) {
    Small(48.dp, 68.dp),
    Large(120.dp, 170.dp),
}
