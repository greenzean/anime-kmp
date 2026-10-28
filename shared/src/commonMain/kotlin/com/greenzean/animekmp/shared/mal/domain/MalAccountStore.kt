package com.greenzean.animekmp.shared.mal.domain

import kotlinx.coroutines.flow.StateFlow

interface MalAccountStore {
    val consentGiven: StateFlow<Boolean>
    val username: StateFlow<String?>

    fun setConsent(given: Boolean)

    fun setUsername(name: String?)
}
