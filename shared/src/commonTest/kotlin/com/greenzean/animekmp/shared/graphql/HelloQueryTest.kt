package com.greenzean.animekmp.shared.graphql

import kotlin.test.Test
import kotlin.test.assertEquals

class HelloQueryTest {
    @Test
    fun generatedOperationIsNamedHello() {
        assertEquals("Hello", HelloQuery().name())
    }
}
