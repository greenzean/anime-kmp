package com.greenzean.animekmp.shared.search.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage

class SearchAnimeUseCase(
    private val repository: MalAnimeRepository,
) {
    suspend operator fun invoke(
        query: String,
        pageStart: Int = 0,
    ): Result<MalSearchPage, MalError> = repository.search(query, pageStart)
}
