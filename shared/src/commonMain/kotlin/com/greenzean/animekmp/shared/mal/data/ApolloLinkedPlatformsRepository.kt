package com.greenzean.animekmp.shared.mal.data

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.greenzean.animekmp.shared.auth.domain.SessionManager
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.graphql.AccountQuery
import com.greenzean.animekmp.shared.graphql.LinkPlatformMutation
import com.greenzean.animekmp.shared.graphql.UnlinkPlatformMutation
import com.greenzean.animekmp.shared.graphql.type.Platform
import com.greenzean.animekmp.shared.mal.domain.LinkedPlatformsRepository
import com.greenzean.animekmp.shared.sync.data.authorized
import com.greenzean.animekmp.shared.sync.data.toResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val AUTHORIZATION = "Authorization"

class ApolloLinkedPlatformsRepository(
    private val client: ApolloClient,
    private val sessions: SessionManager,
    private val sessionStore: SessionStore,
) : LinkedPlatformsRepository {
    private val linked = MutableStateFlow(false)
    private val epoch = MutableStateFlow(0)
    private val writes = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMalLinked(): Flow<Boolean> =
        sessionStore.session
            .map { it != null }
            .distinctUntilChanged()
            .onEach { signedIn ->
                if (!signedIn) {
                    epoch.update { it + 1 }
                    linked.value = false
                }
            }.flatMapLatest { signedIn ->
                if (signedIn) {
                    channelFlow {
                        launch { refresh() }
                        linked.collect { send(it) }
                    }
                } else {
                    flowOf(false)
                }
            }

    override suspend fun refresh(): EmptyResult<DataError> {
        if (sessionStore.get() == null) return Result.Success(Unit)
        val started = epoch.value
        return authorized { token ->
            client.query(AccountQuery()).addHttpHeader(AUTHORIZATION, "Bearer $token").execute()
        }.toDomain(started) { data -> data.account.linkedPlatforms.contains(Platform.MAL) }
    }

    override suspend fun linkMal(): EmptyResult<DataError> = writes.withLock { link() }

    override suspend fun linkMalIf(condition: () -> Boolean): EmptyResult<DataError> =
        writes.withLock { if (condition()) link() else Result.Success(Unit) }

    private suspend fun link(): EmptyResult<DataError> {
        if (sessionStore.get() == null) return Result.Success(Unit)
        val started = epoch.value
        return authorized { token ->
            client
                .mutation(LinkPlatformMutation(Platform.MAL))
                .addHttpHeader(AUTHORIZATION, "Bearer $token")
                .execute()
        }.toDomain(started) { data -> data.linkPlatform.linkedPlatforms.contains(Platform.MAL) }
    }

    override suspend fun unlinkMal(): EmptyResult<DataError> = writes.withLock { unlink() }

    private suspend fun unlink(): EmptyResult<DataError> {
        if (sessionStore.get() == null) return Result.Success(Unit)
        val started = epoch.value
        return authorized { token ->
            client
                .mutation(UnlinkPlatformMutation(Platform.MAL))
                .addHttpHeader(AUTHORIZATION, "Bearer $token")
                .execute()
        }.toDomain(started) { data -> data.unlinkPlatform.linkedPlatforms.contains(Platform.MAL) }
    }

    private suspend fun <D : Operation.Data> Result<ApolloResponse<D>, DataError.Network>.toDomain(
        started: Int,
        isLinked: (D) -> Boolean,
    ): EmptyResult<DataError> =
        when (this) {
            is Result.Error -> {
                this
            }

            is Result.Success -> {
                when (val mapped = data.toResult(isLinked)) {
                    is Result.Error -> {
                        mapped
                    }

                    is Result.Success -> {
                        if (epoch.value == started && sessionStore.get() != null) linked.value = mapped.data
                        Result.Success(Unit)
                    }
                }
            }
        }

    private suspend fun <D : Operation.Data> authorized(
        call: suspend (token: String) -> ApolloResponse<D>,
    ): Result<ApolloResponse<D>, DataError.Network> = sessions.authorized(call = call)
}
