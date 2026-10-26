package com.greenzean.animekmp.shared.mal.domain

import kotlinx.coroutines.CompletableDeferred

class MalRedirectHandler(
    private val config: MalConfig,
) {
    private class Pending(
        val state: String,
        val result: CompletableDeferred<String?> = CompletableDeferred(),
    )

    @kotlin.concurrent.Volatile
    private var pending: Pending? = null

    suspend fun awaitRedirect(
        state: String,
        beforeWaiting: () -> Unit,
    ): String? {
        val waiting = Pending(state)
        pending = waiting
        try {
            beforeWaiting()
            return waiting.result.await()
        } finally {
            if (pending === waiting) pending = null
        }
    }

    fun onRedirect(uri: String) {
        val waiting = pending ?: return
        if (isMalRedirect(uri, config.redirectUri) && malRedirectState(uri) == waiting.state) {
            waiting.result.complete(uri)
        }
    }

    fun onActivityResumed() {
        pending?.result?.complete(null)
    }
}
