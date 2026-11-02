package com.greenzean.animekmp.shared.core.logging

import org.koin.dsl.module

val loggingModule =
    module {
        single<AppLogger> { SinkLogger(getAll<LogSink>()) }
    }
