package com.greenzean.animekmp.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {
    @Test
    fun greetReturnsAppName() {
        assertEquals("Anime Tracker", Greeting().greet())
    }
}
