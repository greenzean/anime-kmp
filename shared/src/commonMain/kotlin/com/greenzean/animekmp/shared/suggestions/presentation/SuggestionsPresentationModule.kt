package com.greenzean.animekmp.shared.suggestions.presentation

import com.greenzean.animekmp.shared.suggestions.domain.GetSuggestionsUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val suggestionsPresentationModule =
    module {
        factoryOf(::GetSuggestionsUseCase)
        viewModelOf(::SuggestionsViewModel)
    }
