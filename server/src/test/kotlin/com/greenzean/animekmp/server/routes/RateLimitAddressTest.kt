package com.greenzean.animekmp.server.routes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RateLimitAddressTest {
    @Test
    fun ipv4IsKeptAsIs() {
        assertEquals("203.0.113.7", rateLimitAddress("203.0.113.7"))
    }

    @Test
    fun ipv6AddressesInTheSame64ShareAKey() {
        assertEquals(
            rateLimitAddress("2001:db8:1:2:aaaa:bbbb:cccc:dddd"),
            rateLimitAddress("2001:db8:1:2:1111:2222:3333:4444"),
        )
    }

    @Test
    fun ipv6AddressesInDifferent64sDoNotShareAKey() {
        assertNotEquals(rateLimitAddress("2001:db8:1:2::1"), rateLimitAddress("2001:db8:1:3::1"))
    }
}
