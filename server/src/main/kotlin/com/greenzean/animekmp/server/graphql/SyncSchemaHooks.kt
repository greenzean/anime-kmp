package com.greenzean.animekmp.server.graphql

import com.expediagroup.graphql.generator.hooks.SchemaGeneratorHooks
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLType
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID
import kotlin.reflect.KType

/**
 * Custom scalars (ADR 0006). `Instant` is an ISO-8601 UTC string, `UUID` a canonical lowercase string.
 * graphql-kotlin ships neither by default.
 * The Apollo side maps them in shared/build.gradle.kts.
 */
object SyncSchemaHooks : SchemaGeneratorHooks {
    val instantScalar: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("Instant")
            .description("A point in time as an ISO-8601 UTC string, e.g. 2026-10-19T12:00:00Z")
            .coercing(InstantCoercing)
            .build()

    val uuidScalar: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("UUID")
            .description("A UUID in canonical string form")
            .coercing(UuidCoercing)
            .build()

    override fun willGenerateGraphQLType(type: KType): GraphQLType? =
        when (type.classifier) {
            Instant::class -> instantScalar
            UUID::class -> uuidScalar
            else -> null
        }
}

private object InstantCoercing : Coercing<Instant, String> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): String =
        (dataFetcherResult as? Instant)?.toString()
            ?: throw CoercingSerializeException("Expected Instant but was ${dataFetcherResult::class.simpleName}")

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Instant =
        parse(input as? String ?: throw CoercingParseValueException("Expected an ISO-8601 string")) {
            CoercingParseValueException(it)
        }

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Instant =
        parse((input as? StringValue)?.value ?: throw CoercingParseLiteralException("Expected an ISO-8601 string")) {
            CoercingParseLiteralException(it)
        }

    private fun parse(
        text: String,
        error: (String) -> RuntimeException,
    ): Instant =
        try {
            Instant.parse(text)
        } catch (e: DateTimeParseException) {
            throw error("Invalid Instant: $text (${e.message})")
        }
}

private object UuidCoercing : Coercing<UUID, String> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): String =
        (dataFetcherResult as? UUID)?.toString()
            ?: throw CoercingSerializeException("Expected UUID but was ${dataFetcherResult::class.simpleName}")

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): UUID =
        parse(input as? String ?: throw CoercingParseValueException("Expected a UUID string")) {
            CoercingParseValueException(it)
        }

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): UUID =
        parse((input as? StringValue)?.value ?: throw CoercingParseLiteralException("Expected a UUID string")) {
            CoercingParseLiteralException(it)
        }

    @Suppress("SwallowedException") // the message is carried into the GraphQL coercion error
    private fun parse(
        text: String,
        error: (String) -> RuntimeException,
    ): UUID =
        try {
            UUID.fromString(text)
        } catch (e: IllegalArgumentException) {
            throw error("Invalid UUID: $text (${e.message})")
        }
}
