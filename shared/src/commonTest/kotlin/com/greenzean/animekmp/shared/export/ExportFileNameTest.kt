package com.greenzean.animekmp.shared.export

import com.greenzean.animekmp.shared.export.domain.ExportFileName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ExportFileNameTest {
    @Test
    fun usesTheUtcDate() {
        assertEquals(
            "animekmp-mal-export-2026-03-05.xml",
            ExportFileName.forInstant(Instant.parse("2026-03-05T10:15:30Z")),
        )
    }

    @Test
    fun usesUtcNotLocalDateNearMidnight() {
        assertEquals(
            "animekmp-mal-export-2026-12-31.xml",
            ExportFileName.forInstant(Instant.parse("2026-12-31T23:59:59Z")),
        )
    }
}
