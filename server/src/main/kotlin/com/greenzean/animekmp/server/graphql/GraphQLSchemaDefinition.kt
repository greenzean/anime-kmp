package com.greenzean.animekmp.server.graphql

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.toSchema
import com.expediagroup.graphql.server.operations.Mutation
import com.expediagroup.graphql.server.operations.Query
import com.greenzean.animekmp.server.account.LinkedPlatformRepository
import com.greenzean.animekmp.server.auth.AuthService
import com.greenzean.animekmp.server.graphql.account.AccountMutation
import com.greenzean.animekmp.server.graphql.account.AccountQuery
import com.greenzean.animekmp.server.graphql.auth.AuthMutation
import com.greenzean.animekmp.server.graphql.sync.SyncMutation
import com.greenzean.animekmp.server.graphql.sync.SyncQuery
import com.greenzean.animekmp.server.sync.SyncRepository
import graphql.schema.GraphQLSchema

object GraphQLSchemaDefinition {
    val packages = listOf("com.greenzean.animekmp.server.graphql")

    fun queries(
        sync: SyncRepository = SyncRepository(),
        linked: LinkedPlatformRepository = LinkedPlatformRepository(),
    ): List<Query> = listOf(HelloQuery(), SyncQuery(sync), AccountQuery(linked))

    fun mutations(
        sync: SyncRepository = SyncRepository(),
        auth: AuthService? = null,
        linked: LinkedPlatformRepository = LinkedPlatformRepository(),
    ): List<Mutation> = listOf(SyncMutation(sync), AuthMutation(auth), AccountMutation(linked))

    fun buildSchema(): GraphQLSchema =
        toSchema(
            config = SchemaGeneratorConfig(supportedPackages = packages, hooks = SyncSchemaHooks),
            queries = queries().map { TopLevelObject(it) },
            mutations = mutations().map { TopLevelObject(it) },
        )
}
