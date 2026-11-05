package com.greenzean.animekmp.shared.mal

import android.content.Context
import com.greenzean.animekmp.shared.mal.data.SharedPreferencesMalAccountStore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesMalAccountStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun newStore() = SharedPreferencesMalAccountStore(context)

    @Test
    fun aNewStoreStartsEmpty() {
        val store = newStore()

        assertFalse(store.consentGiven.value)
        assertNull(store.username.value)
        assertFalse(store.notOnThisDevice.value)
        assertFalse(store.linkPending.value)
    }

    @Test
    fun notOnThisDeviceSurvivesARestart() {
        newStore().setNotOnThisDevice(true)

        assertTrue(newStore().notOnThisDevice.value)
    }

    @Test
    fun notOnThisDeviceCanBeClearedAndStaysCleared() {
        newStore().setNotOnThisDevice(true)
        newStore().setNotOnThisDevice(false)

        assertFalse(newStore().notOnThisDevice.value)
    }

    @Test
    fun linkPendingSurvivesARestart() {
        newStore().setLinkPending(true)

        assertTrue(newStore().linkPending.value)
    }

    @Test
    fun linkPendingCanBeClearedAndStaysCleared() {
        newStore().setLinkPending(true)
        newStore().setLinkPending(false)

        assertFalse(newStore().linkPending.value)
    }

    @Test
    fun consentAndUsernameSurviveARestart() {
        newStore().apply {
            setConsent(true)
            setUsername("francisco")
        }

        val reopened = newStore()

        assertTrue(reopened.consentGiven.value)
        assertEquals("francisco", reopened.username.value)
    }

    @Test
    fun theFlagsDoNotDependOnEachOther() {
        newStore().setLinkPending(true)

        val reopened = newStore()

        assertTrue(reopened.linkPending.value)
        assertFalse(reopened.notOnThisDevice.value)
    }
}
