package com.greenzean.animekmp.server

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class Limits(
    val maxQueryDepth: Int = 10,
    val maxQueryComplexity: Int = 1000,
    val maxBodyBytes: Long = MAX_BODY_BYTES,
    val requestsPerUser: Int = 60,
    val requestsPerIp: Int = 120,
    val rateWindow: Duration = 1.minutes,
)

const val MAX_PUSH_BATCH = 500
private const val MAX_BODY_BYTES = 256L * 1024
