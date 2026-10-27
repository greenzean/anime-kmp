package com.greenzean.animekmp.shared.settings.domain

import kotlinx.coroutines.flow.StateFlow

class ObserveTitleLanguageUseCase(
    private val settings: DisplaySettings,
) {
    operator fun invoke(): StateFlow<TitleLanguage> = settings.titleLanguage
}
