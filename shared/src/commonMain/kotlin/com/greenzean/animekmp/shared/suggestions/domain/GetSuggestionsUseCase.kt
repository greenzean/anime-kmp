package com.greenzean.animekmp.shared.suggestions.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage
import com.greenzean.animekmp.shared.mal.domain.MalSuggestionsRepository

class GetSuggestionsUseCase(
    private val repository: MalSuggestionsRepository,
) {
    suspend operator fun invoke(pageStart: Int = 0): Result<MalSearchPage, MalError> = repository.suggestions(pageStart)
}
