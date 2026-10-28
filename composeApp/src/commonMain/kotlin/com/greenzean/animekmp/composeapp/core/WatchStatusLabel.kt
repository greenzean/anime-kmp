package com.greenzean.animekmp.composeapp.core

import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_completed
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_dropped
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_on_hold
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_plan_to_watch
import com.greenzean.animekmp.composeapp.generated.resources.watch_status_watching
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import org.jetbrains.compose.resources.StringResource

internal fun WatchStatus.label(): StringResource =
    when (this) {
        WatchStatus.WATCHING -> Res.string.watch_status_watching
        WatchStatus.COMPLETED -> Res.string.watch_status_completed
        WatchStatus.ON_HOLD -> Res.string.watch_status_on_hold
        WatchStatus.DROPPED -> Res.string.watch_status_dropped
        WatchStatus.PLAN_TO_WATCH -> Res.string.watch_status_plan_to_watch
    }
