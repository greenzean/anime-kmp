package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result

data class MalUser(
    val name: String,
)

interface MalUserApi {
    suspend fun me(): Result<MalUser, MalApiError>
}
