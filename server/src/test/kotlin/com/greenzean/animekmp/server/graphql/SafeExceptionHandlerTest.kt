package com.greenzean.animekmp.server.graphql

import com.greenzean.animekmp.server.InvalidInput
import graphql.GraphQL
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeExceptionHandlerTest {
    private fun run(failure: () -> Nothing): String {
        val registry = SchemaParser().parse("type Query { boom: String }")
        val wiring = RuntimeWiring.newRuntimeWiring().type("Query") { it.dataFetcher("boom") { failure() } }.build()
        val graphQL =
            GraphQL
                .newGraphQL(SchemaGenerator().makeExecutableSchema(registry, wiring))
                .defaultDataFetcherExceptionHandler(SafeExceptionHandler)
                .build()
        return graphQL.execute("{ boom }").errors.joinToString { it.message }
    }

    @Test
    fun unexpectedErrorsHideTheirText() {
        val message = run { throw IllegalStateException("select * from secret_table") }
        assertFalse(message.contains("secret_table"), message)
        assertTrue(message.contains("Internal server error"), message)
    }

    @Test
    fun illegalArgumentsFromLibrariesHideTheirText() {
        val message = run { throw IllegalArgumentException("column user_secret") }
        assertFalse(message.contains("user_secret"), message)
    }

    @Test
    fun validationErrorsKeepTheirText() {
        assertTrue(run { throw InvalidInput("cursor is too long") }.contains("cursor is too long"))
    }
}
