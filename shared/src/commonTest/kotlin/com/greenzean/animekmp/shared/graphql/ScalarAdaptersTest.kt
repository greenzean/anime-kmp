package com.greenzean.animekmp.shared.graphql

import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.api.json.MapJsonReader
import com.apollographql.apollo.api.json.buildJsonString
import com.greenzean.animekmp.shared.graphql.type.ListEntryChangeInput
import com.greenzean.animekmp.shared.graphql.type.ListStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ScalarAdaptersTest {
    private val instant = Instant.parse("2026-10-19T12:00:00Z")

    @Test
    fun instantRoundTrips() {
        val json = buildJsonString { InstantAdapter.toJson(this, CustomScalarAdapters.Empty, instant) }
        assertEquals("\"2026-10-19T12:00:00Z\"", json)
        val parsed = InstantAdapter.fromJson(MapJsonReader("2026-10-19T12:00:00Z"), CustomScalarAdapters.Empty)
        assertEquals(instant, parsed)
    }

    @Test
    fun generatedOperationsUseMappedTypes() {
        val change =
            ListEntryChangeInput(
                id = "00000000-0000-0000-0000-0000000000aa",
                animeId = 1L,
                status = ListStatus.WATCHING,
                updatedAt = instant,
                deletedAt = Optional.Absent,
            )
        assertEquals("PushChanges", PushChangesMutation(listOf(change)).name())
        assertEquals("ChangesSince", ChangesSinceQuery().name())
    }
}
