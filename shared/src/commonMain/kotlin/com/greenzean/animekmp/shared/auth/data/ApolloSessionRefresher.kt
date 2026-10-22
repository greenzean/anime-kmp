package com.greenzean.animekmp.shared.auth.data

import com.apollographql.apollo.ApolloClient
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.auth.domain.SessionRefresher
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.graphql.RefreshSessionMutation
import com.greenzean.animekmp.shared.sync.data.toResult

/** `client` must not add the session token: a refresh runs while the access token is expired or rejected. */
class ApolloSessionRefresher(
    private val client: ApolloClient,
) : SessionRefresher {
    override suspend fun refresh(refreshToken: String): Result<Session, DataError.Network> =
        client
            .mutation(RefreshSessionMutation(refreshToken))
            .execute()
            .toResult { data ->
                with(data.refreshSession) { Session(accessToken, accessTokenExpiresAt, this.refreshToken) }
            }
}
