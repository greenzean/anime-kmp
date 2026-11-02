package com.greenzean.animekmp.shared.export.domain

import kotlin.time.Instant

private const val DATE_LENGTH = 10

object ExportFileName {
    fun forInstant(instant: Instant): String = "animekmp-mal-export-${instant.toString().take(DATE_LENGTH)}.xml"
}
