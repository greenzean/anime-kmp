package com.greenzean.animekmp.shared.core.data

import org.koin.core.module.Module

/** Platform-only dependencies (provides the SqlDriver). */
expect val platformModule: Module
