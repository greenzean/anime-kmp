package com.greenzean.animekmp.server.graphql

import com.expediagroup.graphql.server.operations.Query

class HelloQuery : Query {
    @Suppress("FunctionOnlyReturningConstant") // placeholder until real queries land
    fun hello(): String = "world"
}
