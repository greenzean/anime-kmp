package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalRedirectHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MalRedirectHandlerTest {
    private val redirectUri = "https://app.example.test/mal-callback"
    private val handler = MalRedirectHandler(MalConfig("client-1", redirectUri))

    @Test
    fun aMatchingRedirectCompletesTheWait() =
        runTest {
            val uri = "$redirectUri?code=c&state=s1"

            val result = async { handler.awaitRedirect("s1") { handler.onRedirect(uri) } }

            assertEquals(uri, result.await())
        }

    @Test
    fun anErrorRedirectWithTheRightStateCompletesTheWait() =
        runTest {
            val uri = "$redirectUri?error=access_denied&state=s1"

            val result = async { handler.awaitRedirect("s1") { handler.onRedirect(uri) } }

            assertEquals(uri, result.await())
        }

    @Test
    fun aRedirectWithAnotherStateIsIgnored() =
        runTest {
            val good = "$redirectUri?code=c&state=s1"

            val result =
                async {
                    handler.awaitRedirect("s1") {
                        handler.onRedirect("$redirectUri?code=forged&state=other")
                        handler.onRedirect("$redirectUri?error=access_denied")
                        handler.onRedirect(good)
                    }
                }

            assertEquals(good, result.await())
        }

    @Test
    fun aRedirectToAnotherUrlIsIgnored() =
        runTest {
            val good = "$redirectUri?code=c&state=s1"

            val result =
                async {
                    handler.awaitRedirect("s1") {
                        handler.onRedirect("https://evil.example.test/mal-callback?code=c&state=s1")
                        handler.onRedirect(good)
                    }
                }

            assertEquals(good, result.await())
        }

    @Test
    fun resumingWithoutARedirectCancelsTheWait() =
        runTest {
            val result = async { handler.awaitRedirect("s1") { handler.onActivityResumed() } }

            assertNull(result.await())
        }

    @Test
    fun theRedirectWinsOverTheResumeThatFollowsIt() =
        runTest {
            val uri = "$redirectUri?code=c&state=s1"

            val result =
                async {
                    handler.awaitRedirect("s1") {
                        handler.onRedirect(uri)
                        handler.onActivityResumed()
                    }
                }

            assertEquals(uri, result.await())
        }

    @Test
    fun aRedirectWithNothingPendingIsDropped() =
        runTest {
            val stale = "$redirectUri?code=old&state=s1"
            handler.onRedirect(stale)
            handler.onActivityResumed()

            val fresh = "$redirectUri?code=new&state=s1"
            val result = async { handler.awaitRedirect("s1") { handler.onRedirect(fresh) } }

            assertEquals(fresh, result.await())
        }
}
