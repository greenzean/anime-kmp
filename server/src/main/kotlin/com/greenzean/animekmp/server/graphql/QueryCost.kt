package com.greenzean.animekmp.server.graphql

import graphql.analysis.FieldComplexityCalculator
import graphql.analysis.FieldComplexityEnvironment

private const val DEFAULT_PULL_LIMIT = 500
private const val PULL_LIMIT_DIVISOR = 5
private const val AUTH_COST = 250
private const val ACCOUNT_COST = 50

private val authFields =
    setOf("signInNonce", "signInWithGoogle", "refreshSession", "signOut", "deleteAccountNonce", "deleteAccount")
private val accountFields = setOf("account", "linkPlatform", "unlinkPlatform")

object QueryCost : FieldComplexityCalculator {
    override fun calculate(
        environment: FieldComplexityEnvironment,
        childComplexity: Int,
    ): Int {
        val own =
            when (environment.field.name) {
                "pushChanges" -> (environment.arguments["changes"] as? Collection<*>)?.size ?: 0
                "changesSince" -> pullCost(environment.arguments["limit"])
                in authFields -> AUTH_COST
                in accountFields -> ACCOUNT_COST
                else -> 0
            }
        return 1 + own + childComplexity
    }

    private fun pullCost(limit: Any?): Int = ((limit as? Number)?.toInt() ?: DEFAULT_PULL_LIMIT) / PULL_LIMIT_DIVISOR
}
