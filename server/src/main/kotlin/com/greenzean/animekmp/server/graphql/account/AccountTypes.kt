package com.greenzean.animekmp.server.graphql.account

enum class Platform {
    MAL,
}

data class Account(
    val linkedPlatforms: List<Platform>,
)
