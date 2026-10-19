package com.greenzean.animekmp.shared.sync.data

/** Where the Apollo client talks to; provided per build by the host app (see androidApp). */
data class ServerConfig(
    val graphqlUrl: String,
)
