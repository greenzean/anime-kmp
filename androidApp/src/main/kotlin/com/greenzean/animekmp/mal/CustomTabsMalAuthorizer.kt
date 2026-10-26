package com.greenzean.animekmp.mal

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.greenzean.animekmp.auth.CurrentActivityHolder
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalAuthorizer
import com.greenzean.animekmp.shared.mal.domain.MalRedirect
import com.greenzean.animekmp.shared.mal.domain.MalRedirectHandler
import com.greenzean.animekmp.shared.mal.domain.parseMalRedirect

class CustomTabsMalAuthorizer(
    private val activities: CurrentActivityHolder,
    private val redirects: MalRedirectHandler,
) : MalAuthorizer {
    override suspend fun authorize(
        url: String,
        state: String,
    ): Result<MalRedirect, MalAuthError> {
        val activity = activities.current
        val browser = activity?.let { CustomTabsClient.getPackageName(it, null) }
        return when {
            activity == null -> Result.Error(MalAuthError.UNKNOWN)
            browser == null -> Result.Error(MalAuthError.NO_BROWSER)
            else -> open(activity, browser, url, state)
        }
    }

    private suspend fun open(
        activity: Activity,
        browser: String,
        url: String,
        state: String,
    ): Result<MalRedirect, MalAuthError> {
        var launched = true
        val redirect =
            redirects.awaitRedirect(state) {
                try {
                    CustomTabsIntent
                        .Builder()
                        .build()
                        .apply { intent.setPackage(browser) }
                        .launchUrl(activity, url.toUri())
                } catch (_: ActivityNotFoundException) {
                    launched = false
                    redirects.onActivityResumed()
                }
            }
        return when {
            !launched -> Result.Error(MalAuthError.NO_BROWSER)
            redirect == null -> Result.Error(MalAuthError.CANCELLED)
            else -> parseMalRedirect(redirect)
        }
    }
}
