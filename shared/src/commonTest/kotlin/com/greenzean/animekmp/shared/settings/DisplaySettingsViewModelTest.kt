package com.greenzean.animekmp.shared.settings

import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import com.greenzean.animekmp.shared.settings.presentation.DisplaySettingsAction
import com.greenzean.animekmp.shared.settings.presentation.DisplaySettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DisplaySettingsViewModelTest {
    private val settings = FakeDisplaySettings()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun titleLanguageDefaultsToEnglish() {
        assertEquals(TitleLanguage.ENGLISH, DisplaySettingsViewModel(settings).state.value.titleLanguage)
    }

    @Test
    fun changingTheTitleLanguageIsStoredAndShown() {
        val viewModel = DisplaySettingsViewModel(settings)

        viewModel.onAction(DisplaySettingsAction.OnTitleLanguageChange(TitleLanguage.MAIN))

        assertEquals(TitleLanguage.MAIN, settings.titleLanguage.value)
        assertEquals(TitleLanguage.MAIN, viewModel.state.value.titleLanguage)
    }
}
