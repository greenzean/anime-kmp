package com.greenzean.animekmp.shared.settings.domain

import kotlinx.coroutines.flow.StateFlow

enum class TitleLanguage { ENGLISH, MAIN }

fun TitleLanguage.pick(
    title: String,
    titleEn: String?,
): String = if (this == TitleLanguage.ENGLISH) titleEn ?: title else title

interface DisplaySettings {
    val titleLanguage: StateFlow<TitleLanguage>

    fun setTitleLanguage(language: TitleLanguage)
}
