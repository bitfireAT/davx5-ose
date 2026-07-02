/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.davdroid.ProductIds
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidTest
class HttpClientBuilderTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var httpClientBuilder: Provider<HttpClientBuilder>

    @Inject
    lateinit var productIds: ProductIds

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
    fun testBuildKtor_CreatesWorkingClient() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("Some Content")
        )

        httpClientBuilder.get().buildKtor().use { client ->
            val response = client.get(server.url("/").toString())
            assertEquals(200, response.status.value)
            assertEquals("Some Content", response.bodyAsText())
        }
    }

    @Test
    fun testCookies() = runTest {
        // Cookies are handled by Ktor's HttpCookies plugin (AcceptAllCookiesStorage),
        // so they're only stored/sent by the Ktor client, not the raw OkHttp client.
        val url = server.url("/test").toString()

        httpClientBuilder.get().buildKtor().use { client ->
            // set cookie for root path (/) and /test path in first response
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader(HttpHeaders.SetCookie, "cookie1=1; path=/")
                    .addHeader(HttpHeaders.SetCookie, "cookie2=2")
                    .setBody("Cookie set")
            )
            client.get(url)
            assertNull(server.takeRequest().getHeader("Cookie"))

            // cookie should be sent with second request
            // second response lets first cookie expire and overwrites second cookie
            server.enqueue(
                MockResponse()
                    .addHeader(HttpHeaders.SetCookie, "cookie1=1a; path=/; Max-Age=0")
                    .addHeader(HttpHeaders.SetCookie, "cookie2=2a")
                    .setResponseCode(200)
            )
            client.get(url)
            val header = server.takeRequest().getHeader("Cookie")
            assertTrue(header == "cookie1=1; cookie2=2" || header == "cookie2=2; cookie1=1")

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
            )
            client.get(url)
            assertEquals("cookie2=2a", server.takeRequest().getHeader("Cookie"))
        }
    }

    @Test
    fun testFollowRedirects_DisabledByDefault() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader(HttpHeaders.Location, server.url("/redirected").toString())
        )

        httpClientBuilder.get().buildKtor().use { client ->
            val response = client.get(server.url("/").toString())
            // redirect is not followed, so we get the 302 response directly
            assertEquals(302, response.status.value)
        }
        // only the initial request was made
        assertEquals(1, server.requestCount)
    }

    @Test
    fun testFollowRedirects_EnabledWhenRequested() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader(HttpHeaders.Location, server.url("/redirected").toString())
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("Target")
        )

        httpClientBuilder.get().followRedirects(true).buildKtor().use { client ->
            val response = client.get(server.url("/").toString())
            assertEquals(200, response.status.value)
            assertEquals("Target", response.bodyAsText())
        }
        // initial request + followed redirect
        assertEquals(2, server.requestCount)
    }

    @Test
    fun testLogging_RedactsSensitiveHeaders() = runTest {
        val secret = "Bearer super-secret-token-12345"

        // capture log messages emitted at FINEST
        val messages = mutableListOf<String>()
        val captureHandler = object : Handler() {
            override fun publish(record: LogRecord) {
                messages += record.message
            }

            override fun flush() {}
            override fun close() {}
        }
        val logger = Logger.getAnonymousLogger().apply {
            level = Level.FINEST
            useParentHandlers = false
            captureHandler.level = Level.FINEST
            addHandler(captureHandler)
        }

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.SetCookie, "session=secret-cookie-value")
                .setBody("OK")
        )

        httpClientBuilder.get()
            .setLogger(logger)
            .loggerInterceptorLevel(LogLevel.ALL)
            .buildKtor().use { client ->
                client.get(server.url("/").toString()) {
                    header(HttpHeaders.Authorization, secret)
                }
            }

        val log = messages.joinToString("\n")
        // sensitive header values must not be logged
        assertFalse(log.contains("super-secret-token-12345"))
        assertFalse(log.contains("secret-cookie-value"))
        // they must be redacted
        assertTrue(log.contains("***"))
    }

    @Test
    fun testUserAgentAndAcceptLanguage() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val origLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.of("de", "AT"))

            httpClientBuilder.get().buildKtor().use { client ->
                client.get(server.url("/").toString())
            }

            val request = server.takeRequest()
            assertEquals(
                productIds.httpUserAgent,
                request.getHeader(HttpHeaders.UserAgent)
            )
            assertEquals(
                "de-AT, de;q=0.7, *;q=0.5",
                request.getHeader(HttpHeaders.AcceptLanguage)
            )
        } finally {
            Locale.setDefault(origLocale)
        }
    }

}
