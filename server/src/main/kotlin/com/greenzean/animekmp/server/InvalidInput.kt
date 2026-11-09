package com.greenzean.animekmp.server

import graphql.GraphqlErrorException

class InvalidInput(
    message: String,
) : GraphqlErrorException(
        newErrorException().message(message).extensions(mapOf<String, Any>("code" to "BAD_USER_INPUT")),
    )

fun requireInput(
    condition: Boolean,
    message: () -> String,
) {
    if (!condition) throw InvalidInput(message())
}
