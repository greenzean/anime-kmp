package com.greenzean.animekmp.shared.sync.data

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.greenzean.animekmp.shared.auth.domain.SessionManager
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.core.logging.AppLogger

private const val TAG = "Sync"

internal suspend fun <D : Operation.Data> SessionManager.authorized(
    logger: AppLogger? = null,
    call: suspend (token: String) -> ApolloResponse<D>,
): Result<ApolloResponse<D>, DataError.Network> =
    when (val token = accessToken()) {
        is Result.Error -> {
            token
        }

        is Result.Success -> {
            val response = call(token.data)
            if (response.isUnauthenticated()) {
                logger?.warn(TAG, "request unauthenticated, renewing session")
                when (val renewed = renewAfterRejection(token.data)) {
                    is Result.Error -> renewed
                    is Result.Success -> Result.Success(call(renewed.data))
                }
            } else {
                Result.Success(response)
            }
        }
    }
