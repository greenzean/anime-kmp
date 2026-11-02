package com.greenzean.animekmp.shared.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals

class LogcatMinLevelTest {
    @Test
    fun aDebuggableBuildLogsFromDebug() {
        assertEquals(LogLevel.Debug, logcatMinLevel(debuggable = true))
    }

    @Test
    fun aReleaseBuildLogsFromWarn() {
        assertEquals(LogLevel.Warn, logcatMinLevel(debuggable = false))
    }
}
