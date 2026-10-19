package com.greenzean.animekmp.server.graphql

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.toSchema
import com.expediagroup.graphql.server.operations.Mutation
import com.expediagroup.graphql.server.operations.Query
import com.greenzean.animekmp.server.graphql.sync.SyncMutation
import com.greenzean.animekmp.server.graphql.sync.SyncQuery
import com.greenzean.animekmp.server.sync.SyncRepository
import graphql.schema.GraphQLSchema

/**
 * The single definition of the GraphQL schema. The runtime plugin and the SDL export test both read it,
 * so the committed SDL is exactly what the server serves (ADR 0004).
 */
object GraphQLSchemaDefinition {
    val packages = listOf("com.greenzean.animekmp.server.graphql")

    /** `sync` is only invoked by resolvers; the schema export builds without a database. */
    fun queries(sync: SyncRepository = SyncRepository()): List<Query> = listOf(HelloQuery(), SyncQuery(sync))

    fun mutations(sync: SyncRepository = SyncRepository()): List<Mutation> = listOf(SyncMutation(sync))

    fun buildSchema(): GraphQLSchema =
        toSchema(
            config = SchemaGeneratorConfig(supportedPackages = packages, hooks = SyncSchemaHooks),
            queries = queries().map { TopLevelObject(it) },
            mutations = mutations().map { TopLevelObject(it) },
        )
}
