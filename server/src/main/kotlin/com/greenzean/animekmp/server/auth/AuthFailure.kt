package com.greenzean.animekmp.server.auth

import graphql.GraphqlErrorException

private const val GENERIC_MESSAGE = "Authentication failed"

/**
 * Every failed auth check looks the same to the client, so it cannot tell which check failed. `reason` is for the
 * server log only and must never contain a token. The error code lets the app tell "sign in again" from other errors.
 */
class AuthFailure(
    val reason: String,
    cause: Throwable? = null,
) : GraphqlErrorException(
        newErrorException()
            .message(GENERIC_MESSAGE)
            .cause(cause)
            .extensions(mapOf<String, Any>("code" to "UNAUTHENTICATED")),
    )
