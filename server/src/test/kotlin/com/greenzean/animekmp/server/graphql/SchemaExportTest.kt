package com.greenzean.animekmp.server.graphql

import graphql.schema.idl.SchemaPrinter
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exports the SDL the client compiles against (ADR 0004). Fails when the committed file has drifted;
 * run `./gradlew :server:test -PupdateSchema` to rewrite it.
 */
class SchemaExportTest {
    private val schemaFile = File(System.getProperty("schemaFile"))
    private val update = System.getProperty("updateSchema").toBoolean()

    @Test
    fun committedSchemaMatchesServer() {
        val sdl = SchemaPrinter().print(GraphQLSchemaDefinition.buildSchema())
        if (update) {
            schemaFile.parentFile.mkdirs()
            schemaFile.writeText(sdl)
            return
        }
        assertTrue(
            schemaFile.exists(),
            "${schemaFile.path} is missing. Run ./gradlew :server:test -PupdateSchema",
        )
        assertEquals(
            schemaFile.readText(),
            sdl,
            "schema.graphqls is out of date. Run ./gradlew :server:test -PupdateSchema and commit the diff",
        )
    }
}
