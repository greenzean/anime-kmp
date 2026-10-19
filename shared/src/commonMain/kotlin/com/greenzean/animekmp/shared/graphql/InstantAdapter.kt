package com.greenzean.animekmp.shared.graphql

import com.apollographql.apollo.api.Adapter
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.api.json.JsonWriter
import kotlin.time.Instant

/**
 * Maps the server's `Instant` scalar (ISO-8601 UTC string, ADR 0006) to [Instant].
 * `UUID` and `Long` map to String and Long in build.gradle.kts.
 */
object InstantAdapter : Adapter<Instant> {
    override fun fromJson(
        reader: JsonReader,
        customScalarAdapters: CustomScalarAdapters,
    ): Instant = Instant.parse(reader.nextString()!!)

    override fun toJson(
        writer: JsonWriter,
        customScalarAdapters: CustomScalarAdapters,
        value: Instant,
    ) {
        writer.value(value.toString())
    }
}
