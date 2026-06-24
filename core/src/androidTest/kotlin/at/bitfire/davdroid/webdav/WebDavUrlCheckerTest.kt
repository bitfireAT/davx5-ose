/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class WebDavUrlCheckerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var webDavUrlChecker: WebDavUrlChecker

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    val web = MockWebServer()
    val url = web.url("/")

    @Test
    fun getWebDavUrl_NoDavHeader() = runTest {
        web.enqueue(MockResponse().setResponseCode(200))

        val result = webDavUrlChecker.getWebDavUrl(url = url, credentials = null)

        assertNull(result)
    }

    @Test
    fun getWebDavUrl_DavClass1() = runTest {
        web.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("DAV: 1"))

        val result = webDavUrlChecker.getWebDavUrl(url = url, credentials = null)

        assertEquals(url, result)
    }

    @Test
    fun getWebDavUrl_DavClass2() = runTest {
        web.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("DAV: 1, 2"))

        val result = webDavUrlChecker.getWebDavUrl(url = url, credentials = null)

        assertEquals(url, result)
    }

    @Test
    fun getWebDavUrl_DavClass3() = runTest {
        web.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("DAV: 1, 3"))

        val result = webDavUrlChecker.getWebDavUrl(url = url, credentials = null)

        assertEquals(url, result)
    }
}
