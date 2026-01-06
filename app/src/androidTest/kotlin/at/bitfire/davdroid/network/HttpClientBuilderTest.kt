/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.security.NetworkSecurityPolicy
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.test.runTest
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
import javax.inject.Provider

@HiltAndroidTest
class HttpClientBuilderTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var httpClientBuilder: Provider<HttpClientBuilder>

    lateinit var server: MockWebServer

    @Before
    fun setUp() {
        hiltRule.inject()

        server = MockWebServer()
        server.start(30000)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }


    @Test
    fun testBuild_SharesConnectionPoolAndDispatcher() {
        val client1 = httpClientBuilder.get().build()
        val client2 = httpClientBuilder.get().build()
        assertEquals(client1.connectionPool, client2.connectionPool)
        assertEquals(client1.dispatcher, client2.dispatcher)
    }

    @Test
    fun testBuildKtor_CreatesWorkingClient() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("Some Content"))

        httpClientBuilder.get().buildKtor().use { client ->
            val response = client.get(server.url("/").toString())
            assertEquals(200, response.status.value)
            assertEquals("Some Content", response.bodyAsText())
        }
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

        val httpClient = httpClientBuilder.get().build()
        httpClient.newCall(Request.Builder()
                .get().url(url)
                .build()).execute()
        assertNull(server.takeRequest().getHeader("Cookie"))

        // cookie should be sent with second request
        // second response lets first cookie expire and overwrites second cookie
        server.enqueue(MockResponse()
                .addHeader("Set-Cookie", "cookie1=1a; path=/; Max-Age=0")
                .addHeader("Set-Cookie", "cookie2=2a")
                .setResponseCode(200))
        httpClient.newCall(Request.Builder()
                .get().url(url)
                .build()).execute()
        val header = server.takeRequest().getHeader("Cookie")
        assertTrue(header == "cookie1=1; cookie2=2" || header == "cookie2=2; cookie1=1")

        server.enqueue(MockResponse()
                .setResponseCode(200))
        httpClient.newCall(Request.Builder()
                .get().url(url)
                .build()).execute()
        assertEquals("cookie2=2a", server.takeRequest().getHeader("Cookie"))
    }

}
