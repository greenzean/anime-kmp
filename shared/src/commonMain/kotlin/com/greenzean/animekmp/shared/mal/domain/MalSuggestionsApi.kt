package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result

interface MalSuggestionsApi {
    suspend fun suggestions(
        limit: Int,
        offset: Int,
    ): Result<MalSearchPage, MalApiError>
}
