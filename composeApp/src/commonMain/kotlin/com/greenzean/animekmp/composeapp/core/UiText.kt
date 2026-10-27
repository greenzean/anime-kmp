package com.greenzean.animekmp.composeapp.core

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString as resourceString

sealed interface UiText {
    data class DynamicString(
        val value: String,
    ) : UiText

    class StringResource(
        val id: org.jetbrains.compose.resources.StringResource,
        val args: Array<Any> = emptyArray(),
    ) : UiText

    @Composable
    fun asString(): String =
        when (this) {
            is DynamicString -> value
            is StringResource -> stringResource(id, *args)
        }

    suspend fun getString(): String =
        when (this) {
            is DynamicString -> value
            is StringResource -> resourceString(id, *args)
        }
}
