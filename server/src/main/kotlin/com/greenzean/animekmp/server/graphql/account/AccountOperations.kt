package com.greenzean.animekmp.server.graphql.account

import com.expediagroup.graphql.server.operations.Mutation
import com.expediagroup.graphql.server.operations.Query
import com.greenzean.animekmp.server.account.LinkedPlatformRepository
import com.greenzean.animekmp.server.auth.AuthFailure
import com.greenzean.animekmp.server.graphql.auth.requireUserId
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import java.util.UUID

private const val FOREIGN_KEY_VIOLATION = "23503"

class AccountQuery(
    private val linked: LinkedPlatformRepository,
) : Query {
    fun account(env: DataFetchingEnvironment): Account {
        val userId = env.requireUserId()
        linked.requireUser(userId)
        return linked.accountOf(userId)
    }
}

class AccountMutation(
    private val linked: LinkedPlatformRepository,
) : Mutation {
    fun linkPlatform(
        env: DataFetchingEnvironment,
        platform: Platform,
    ): Account {
        val userId = env.requireUserId()
        linked.requireUser(userId)
        try {
            linked.link(userId, platform.name)
        } catch (e: ExposedSQLException) {
            if (e.sqlState != FOREIGN_KEY_VIOLATION) throw e
            throw AuthFailure("The account no longer exists")
        }
        return linked.accountOf(userId)
    }

    fun unlinkPlatform(
        env: DataFetchingEnvironment,
        platform: Platform,
    ): Account {
        val userId = env.requireUserId()
        linked.requireUser(userId)
        linked.unlink(userId, platform.name)
        return linked.accountOf(userId)
    }
}

private fun LinkedPlatformRepository.accountOf(userId: UUID) = Account(platforms(userId).map { Platform.valueOf(it) })
