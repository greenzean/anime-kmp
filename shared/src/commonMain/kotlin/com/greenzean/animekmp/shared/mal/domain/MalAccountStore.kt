package com.greenzean.animekmp.shared.mal.domain

import kotlinx.coroutines.flow.StateFlow

interface MalAccountStore {
    val consentGiven: StateFlow<Boolean>
    val username: StateFlow<String?>
    val notOnThisDevice: StateFlow<Boolean>
    val linkPending: StateFlow<Boolean>

    fun setConsent(given: Boolean)

    fun setUsername(name: String?)

    fun setNotOnThisDevice(value: Boolean)

    fun setLinkPending(value: Boolean)
}
