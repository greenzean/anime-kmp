package com.greenzean.animekmp.composeapp.settings

import com.greenzean.animekmp.composeapp.core.UiText
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_error_cancelled
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_error_denied
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_error_no_browser
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_error_no_internet
import com.greenzean.animekmp.composeapp.generated.resources.settings_mal_error_other
import com.greenzean.animekmp.shared.mal.presentation.MalAccountError

internal fun MalAccountError.toUiText(): UiText =
    when (this) {
        MalAccountError.Cancelled -> UiText.StringResource(Res.string.settings_mal_error_cancelled)
        MalAccountError.AccessDenied -> UiText.StringResource(Res.string.settings_mal_error_denied)
        MalAccountError.NoBrowser -> UiText.StringResource(Res.string.settings_mal_error_no_browser)
        MalAccountError.NoInternet -> UiText.StringResource(Res.string.settings_mal_error_no_internet)
        MalAccountError.Other -> UiText.StringResource(Res.string.settings_mal_error_other)
    }
