package com.greenzean.animekmp.shared.details.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalAnimeDetails
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalError

class GetAnimeDetailsUseCase(
    private val repository: MalAnimeRepository,
) {
    suspend operator fun invoke(malId: Int): Result<MalAnimeDetails, MalError> = repository.details(malId)
}
