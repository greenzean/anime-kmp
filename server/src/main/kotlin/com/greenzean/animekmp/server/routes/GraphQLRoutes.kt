package com.greenzean.animekmp.server.routes

import com.expediagroup.graphql.server.ktor.graphQLPostRoute
import com.expediagroup.graphql.server.ktor.graphiQLRoute
import io.ktor.server.routing.Route

fun Route.graphQLRoutes() {
    graphQLPostRoute()
    graphiQLRoute()
}
