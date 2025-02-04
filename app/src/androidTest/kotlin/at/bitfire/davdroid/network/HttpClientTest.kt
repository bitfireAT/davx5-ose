/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.content.ContentResolver
import android.security.NetworkSecurityPolicy
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class HttpClientTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var httpClientBuilder: HttpClient.Builder

    lateinit var httpClient: HttpClient
    lateinit var server: MockWebServer

    @Before
    fun setUp() {
        ContentResolver.setMasterSyncAutomatically(false)
        hiltRule.inject()

        httpClient = httpClientBuilder.build()

        server = MockWebServer()
        server.start(30000)
    }

    @After
    fun tearDown() {
        server.shutdown()
        httpClient.close()
    }


    @Test
    fun testCookies() {
        Assume.assumeTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
        val url = server.url("/test")

        // set cookie for root path (/) and /test path in first response
        server.enqueue(MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "cookie1=1; path=/")
                .addHeader("Set-Cookie", "cookie2=2")
                .setBody("Cookie set"))
        httpClient.okHttpClient.newCall(Request.Builder()
                .get().url(url)
                .build()).execute()
        assertNull(server.takeRequest().getHeader("Cookie"))

        // cookie should be sent with second request
        // second response lets first cookie expire and overwrites second cookie
        server.enqueue(MockResponse()
                .addHeader("Set-Cookie", "cookie1=1a; path=/; Max-Age=0")
                .addHeader("Set-Cookie", "cookie2=2a")
                .setResponseCode(200))
        httpClient.okHttpClient.newCall(Request.Builder()
                .get().url(url)
                .build()).execute()
        val header = server.takeRequest().getHeader("Cookie")
        assertTrue(header == "cookie1=1; cookie2=2" || header == "cookie2=2; cookie1=1")

        server.enqueue(MockResponse()
                .setResponseCode(200))
        httpClient.okHttpClient.newCall(Request.Builder()
                .get().url(url)
                .build()).execute()
        assertEquals("cookie2=2a", server.takeRequest().getHeader("Cookie"))
    }

}
