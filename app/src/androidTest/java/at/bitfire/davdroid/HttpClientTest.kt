/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.security.NetworkSecurityPolicy
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume
import org.junit.Before
import org.junit.Test

class HttpClientTest {

    lateinit var server: MockWebServer
    lateinit var httpClient: HttpClient

    @Before
    fun setUp() {
        httpClient = HttpClient.Builder().build()

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
        assertEquals("cookie2=2; cookie1=1", server.takeRequest().getHeader("Cookie"))

        server.enqueue(MockResponse()
                .setResponseCode(200))
        httpClient.okHttpClient.newCall(Request.Builder()
                .get().url(url)
                .build()).execute()
        assertEquals("cookie2=2a", server.takeRequest().getHeader("Cookie"))
    }

}
