package com.greenzean.animekmp.shared.auth.data

import com.apollographql.apollo.ApolloClient
import com.greenzean.animekmp.shared.auth.domain.AuthApi
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.graphql.SignInNonceMutation
import com.greenzean.animekmp.shared.graphql.SignInWithGoogleMutation
import com.greenzean.animekmp.shared.sync.data.toResult

/** `client` must not add the session token: the user is signing in. */
class ApolloAuthApi(
    private val client: ApolloClient,
) : AuthApi {
    override suspend fun signInNonce(): Result<String, DataError.Network> =
        client.mutation(SignInNonceMutation()).execute().toResult { it.signInNonce }

    override suspend fun signInWithGoogle(idToken: String): Result<Session, DataError.Network> =
        client
            .mutation(SignInWithGoogleMutation(idToken))
            .execute()
            .toResult { data ->
                with(data.signInWithGoogle) { Session(accessToken, accessTokenExpiresAt, this.refreshToken) }
            }
}
