package com.greenzean.animekmp.server.routes

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
)
