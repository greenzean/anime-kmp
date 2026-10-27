package com.greenzean.animekmp.composeapp

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

fun str(
    resource: StringResource,
    vararg args: Any,
): String = runBlocking { getString(resource, *args) }

fun plural(
    resource: PluralStringResource,
    quantity: Int,
    vararg args: Any,
): String = runBlocking { getPluralString(resource, quantity, *args) }
