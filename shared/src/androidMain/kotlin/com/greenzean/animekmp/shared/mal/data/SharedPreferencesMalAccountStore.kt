package com.greenzean.animekmp.shared.mal.data

import android.content.Context
import com.greenzean.animekmp.shared.mal.domain.MalAccountStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val FILE = "mal_account"
private const val KEY_CONSENT = "consent_given"
private const val KEY_USERNAME = "username"

class SharedPreferencesMalAccountStore(
    context: Context,
) : MalAccountStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val consent = MutableStateFlow(prefs.getBoolean(KEY_CONSENT, false))
    private val name = MutableStateFlow(prefs.getString(KEY_USERNAME, null))

    override val consentGiven: StateFlow<Boolean> = consent.asStateFlow()
    override val username: StateFlow<String?> = name.asStateFlow()

    override fun setConsent(given: Boolean) {
        prefs.edit().putBoolean(KEY_CONSENT, given).apply()
        consent.value = given
    }

    override fun setUsername(name: String?) {
        prefs.edit().putString(KEY_USERNAME, name).apply()
        this.name.value = name
    }
}
