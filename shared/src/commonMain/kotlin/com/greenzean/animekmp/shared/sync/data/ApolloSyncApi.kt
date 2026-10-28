package com.greenzean.animekmp.shared.sync.data

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.greenzean.animekmp.shared.auth.domain.SessionManager
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.graphql.ChangesSinceQuery
import com.greenzean.animekmp.shared.graphql.PushChangesMutation
import com.greenzean.animekmp.shared.graphql.type.ListEntryChangeInput
import com.greenzean.animekmp.shared.graphql.type.ListStatus
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.sync.domain.ChangesPage
import com.greenzean.animekmp.shared.sync.domain.PushOutcome
import com.greenzean.animekmp.shared.sync.domain.PushResult
import com.greenzean.animekmp.shared.sync.domain.SyncApi
import com.greenzean.animekmp.shared.sync.domain.SyncChange
import com.greenzean.animekmp.shared.graphql.type.PushOutcome as GqlPushOutcome

class ApolloSyncApi(
    private val client: ApolloClient,
    private val sessions: SessionManager,
) : SyncApi {
    override suspend fun push(changes: List<SyncChange>): Result<List<PushResult>, DataError.Network> =
        authorized { token ->
            client
                .mutation(PushChangesMutation(changes.map { it.toInput() }))
                .addHttpHeader(AUTHORIZATION, "Bearer $token")
                .execute()
        }.flatMap { response ->
            response.toResult { data ->
                data.pushChanges.results.map { PushResult(it.id, it.outcome.toDomain(), it.entryId) }
            }
        }

    override suspend fun pull(
        cursor: String?,
        limit: Int,
    ): Result<ChangesPage, DataError.Network> =
        authorized { token ->
            client
                .query(ChangesSinceQuery(Optional.presentIfNotNull(cursor), Optional.Present(limit)))
                .addHttpHeader(AUTHORIZATION, "Bearer $token")
                .execute()
        }.flatMap { response ->
            response.toResult { data ->
                val page = data.changesSince
                ChangesPage(
                    nextCursor = page.nextCursor,
                    hasMore = page.hasMore,
                    changes =
                        page.changes.map {
                            SyncChange(
                                id = it.id,
                                malId = it.malId,
                                status = WatchStatus.valueOf(it.status.rawValue),
                                progress = it.progress,
                                score = it.score,
                                updatedAt = it.updatedAt,
                                deletedAt = it.deletedAt,
                            )
                        },
                )
            }
        }

    private suspend fun <D : Operation.Data> authorized(
        call: suspend (token: String) -> ApolloResponse<D>,
    ): Result<ApolloResponse<D>, DataError.Network> =
        sessions.accessToken().flatMap { token ->
            val response = call(token)
            if (response.isUnauthenticated()) {
                sessions.renewAfterRejection(token).flatMap { renewed -> Result.Success(call(renewed)) }
            } else {
                Result.Success(response)
            }
        }
}

private const val AUTHORIZATION = "Authorization"
private const val UNAUTHENTICATED = "UNAUTHENTICATED"

private inline fun <T, R> Result<T, DataError.Network>.flatMap(
    next: (T) -> Result<R, DataError.Network>,
): Result<R, DataError.Network> =
    when (this) {
        is Result.Error -> this
        is Result.Success -> next(data)
    }

internal fun ApolloResponse<*>.isUnauthenticated(): Boolean =
    errors?.any { it.extensions?.get("code") == UNAUTHENTICATED } == true

private fun SyncChange.toInput() =
    ListEntryChangeInput(
        id = id,
        malId = malId,
        status = ListStatus.valueOf(status.name),
        progress = progress,
        score = Optional.presentIfNotNull(score),
        updatedAt = updatedAt,
        deletedAt = Optional.presentIfNotNull(deletedAt),
    )

private fun GqlPushOutcome.toDomain(): PushOutcome =
    when (this) {
        GqlPushOutcome.APPLIED -> PushOutcome.APPLIED
        GqlPushOutcome.IGNORED_STALE -> PushOutcome.IGNORED_STALE
        GqlPushOutcome.REJECTED_INVALID -> PushOutcome.REJECTED_INVALID
        else -> PushOutcome.REJECTED_INVALID
    }

internal inline fun <D : Operation.Data, T> ApolloResponse<D>.toResult(map: (D) -> T): Result<T, DataError.Network> {
    val data = data
    return when {
        exception != null -> Result.Error(exception!!.toDataError())
        isUnauthenticated() -> Result.Error(DataError.Network.UNAUTHORIZED)
        hasErrors() -> Result.Error(DataError.Network.SERVER_ERROR)
        data == null -> Result.Error(DataError.Network.UNKNOWN)
        else -> Result.Success(map(data))
    }
}

internal fun ApolloException.toDataError(): DataError.Network =
    when (this) {
        is ApolloNetworkException -> DataError.Network.NO_INTERNET
        is ApolloHttpException -> statusCode.toDataError()
        else -> DataError.Network.UNKNOWN
    }

private object HttpStatus {
    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val REQUEST_TIMEOUT = 408
    const val CONFLICT = 409
    const val PAYLOAD_TOO_LARGE = 413
    const val TOO_MANY_REQUESTS = 429
    const val SERVICE_UNAVAILABLE = 503
    val SERVER_ERRORS = 500..599
}

internal fun Int.toDataError(): DataError.Network =
    when (this) {
        HttpStatus.BAD_REQUEST -> DataError.Network.BAD_REQUEST
        HttpStatus.UNAUTHORIZED -> DataError.Network.UNAUTHORIZED
        HttpStatus.FORBIDDEN -> DataError.Network.FORBIDDEN
        HttpStatus.NOT_FOUND -> DataError.Network.NOT_FOUND
        HttpStatus.REQUEST_TIMEOUT -> DataError.Network.REQUEST_TIMEOUT
        HttpStatus.CONFLICT -> DataError.Network.CONFLICT
        HttpStatus.PAYLOAD_TOO_LARGE -> DataError.Network.PAYLOAD_TOO_LARGE
        HttpStatus.TOO_MANY_REQUESTS -> DataError.Network.TOO_MANY_REQUESTS
        HttpStatus.SERVICE_UNAVAILABLE -> DataError.Network.SERVICE_UNAVAILABLE
        in HttpStatus.SERVER_ERRORS -> DataError.Network.SERVER_ERROR
        else -> DataError.Network.UNKNOWN
    }
