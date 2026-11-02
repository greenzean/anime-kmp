package com.greenzean.animekmp.shared.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals

class LogcatMinLevelTest {
    @Test
    fun aDebuggableBuildLogsFromTrace() {
        assertEquals(LogLevel.Trace, logcatMinLevel(debuggable = true))
    }

    @Test
    fun aReleaseBuildLogsFromWarn() {
        assertEquals(LogLevel.Warn, logcatMinLevel(debuggable = false))
    }
}
