package com.greenzean.animekmp.server.graphql

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.toSchema
import com.expediagroup.graphql.server.operations.Query
import graphql.schema.GraphQLSchema

/**
 * The single definition of the GraphQL schema. The runtime plugin and the SDL export test both read it,
 * so the committed SDL is exactly what the server serves (ADR 0004).
 */
object GraphQLSchemaDefinition {
    val packages = listOf("com.greenzean.animekmp.server.graphql")

    fun queries(): List<Query> = listOf(HelloQuery())

    fun buildSchema(): GraphQLSchema =
        toSchema(
            config = SchemaGeneratorConfig(supportedPackages = packages),
            queries = queries().map { TopLevelObject(it) },
        )
}
