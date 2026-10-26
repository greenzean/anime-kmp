package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalRedirect
import com.greenzean.animekmp.shared.mal.domain.isMalRedirect
import com.greenzean.animekmp.shared.mal.domain.malRedirectState
import com.greenzean.animekmp.shared.mal.domain.parseMalRedirect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MalRedirectParserTest {
    private val base = "https://app.example.test/mal-callback"

    @Test
    fun codeAndStateAreRead() {
        assertEquals(
            Result.Success(MalRedirect("abc", "xyz")),
            parseMalRedirect("$base?code=abc&state=xyz"),
        )
    }

    @Test
    fun accessDeniedIsReported() {
        assertEquals(
            Result.Error(MalAuthError.ACCESS_DENIED),
            parseMalRedirect("$base?error=access_denied&state=xyz"),
        )
    }

    @Test
    fun otherErrorsAreUnknown() {
        assertEquals(Result.Error(MalAuthError.UNKNOWN), parseMalRedirect("$base?error=server_error&state=xyz"))
    }

    @Test
    fun missingCodeIsUnknown() {
        assertEquals(Result.Error(MalAuthError.UNKNOWN), parseMalRedirect("$base?state=xyz"))
    }

    @Test
    fun missingStateIsUnknown() {
        assertEquals(Result.Error(MalAuthError.UNKNOWN), parseMalRedirect("$base?code=abc"))
    }

    @Test
    fun malformedRedirectIsUnknown() {
        assertEquals(Result.Error(MalAuthError.UNKNOWN), parseMalRedirect("http://[::1"))
    }

    @Test
    fun redirectMatchingIsExactOnSchemeHostAndPath() {
        assertTrue(isMalRedirect("$base?code=a&state=b", base))
        assertTrue(isMalRedirect("$base/?code=a&state=b", base))
        assertTrue(isMalRedirect("https://APP.example.test/mal-callback?x=1", base))
        assertFalse(isMalRedirect("http://app.example.test/mal-callback", base))
        assertFalse(isMalRedirect("https://evil.example.test/mal-callback", base))
        assertFalse(isMalRedirect("https://app.example.test/mal-callback-x", base))
        assertFalse(isMalRedirect("https://app.example.test/mal-callback/more", base))
        assertFalse(isMalRedirect("https://app.example.test:8443/mal-callback", base))
        assertFalse(isMalRedirect("$base?code=a", ""))
        assertFalse(isMalRedirect("not a url", base))
    }

    @Test
    fun stateIsReadFromErrorRedirectsToo() {
        assertEquals("xyz", malRedirectState("$base?error=access_denied&state=xyz"))
        assertNull(malRedirectState("$base?error=access_denied"))
    }
}
