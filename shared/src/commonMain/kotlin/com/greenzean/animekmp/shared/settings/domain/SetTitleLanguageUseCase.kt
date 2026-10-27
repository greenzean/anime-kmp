package com.greenzean.animekmp.shared.settings.domain

class SetTitleLanguageUseCase(
    private val settings: DisplaySettings,
) {
    operator fun invoke(language: TitleLanguage) = settings.setTitleLanguage(language)
}
