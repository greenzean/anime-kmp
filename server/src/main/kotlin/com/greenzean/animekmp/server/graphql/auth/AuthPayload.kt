package com.greenzean.animekmp.server.graphql.auth

import java.time.Instant

data class AuthPayload(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
)
