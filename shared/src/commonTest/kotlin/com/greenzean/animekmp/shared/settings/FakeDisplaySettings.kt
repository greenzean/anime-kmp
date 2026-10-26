package com.greenzean.animekmp.shared.settings

import com.greenzean.animekmp.shared.settings.domain.DisplaySettings
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeDisplaySettings(
    initial: TitleLanguage = TitleLanguage.ENGLISH,
) : DisplaySettings {
    private val language = MutableStateFlow(initial)

    override val titleLanguage: StateFlow<TitleLanguage> = language

    override fun setTitleLanguage(language: TitleLanguage) {
        this.language.value = language
    }
}
