package com.greenzean.animekmp.shared.settings.data

import android.content.Context
import com.greenzean.animekmp.shared.settings.domain.DisplaySettings
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val FILE = "display_settings"
private const val KEY_TITLE_LANGUAGE = "title_language"

class SharedPreferencesDisplaySettings(
    context: Context,
) : DisplaySettings {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val language = MutableStateFlow(read())

    override val titleLanguage: StateFlow<TitleLanguage> = language.asStateFlow()

    override fun setTitleLanguage(language: TitleLanguage) {
        prefs.edit().putString(KEY_TITLE_LANGUAGE, language.name).apply()
        this.language.value = language
    }

    private fun read(): TitleLanguage =
        prefs
            .getString(KEY_TITLE_LANGUAGE, null)
            ?.let { name -> TitleLanguage.entries.firstOrNull { it.name == name } }
            ?: TitleLanguage.ENGLISH
}
