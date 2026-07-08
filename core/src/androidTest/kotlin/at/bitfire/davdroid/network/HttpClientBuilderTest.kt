/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.davdroid.MockEngineQueue
import at.bitfire.davdroid.MockEngineUtils.Default
import at.bitfire.davdroid.MockEngineUtils.basic
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.okhttp.OkHttpEngine
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headers
import io.ktor.http.headersOf
import io.ktor.http.renderSetCookieHeader
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.Proxy
import java.util.Locale
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class HttpClientBuilderTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockKRule = MockKRule(this)

    @Inject
    lateinit var httpClientBuilder: HttpClientBuilder

    @Inject
    lateinit var productIds: ProductIds

    @BindValue @MockK(relaxed = true)
    lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test
    fun testBuild_CreatesWorkingClient() = runTest {
        val engine = MockEngine.basic("Some Content")

        httpClientBuilder.build(engine).use { client ->
            val response = client.get("https://example.com/")
            assertEquals(200, response.status.value)
            assertEquals("Some Content", response.bodyAsText())
        }
    }

    @Test
    fun testBuildProxy_System() = runTest {
        every { settingsManager.getInt(Settings.PROXY_TYPE) } returns Settings.PROXY_TYPE_SYSTEM

        httpClientBuilder.build().use { client ->
            assertNull((client.engine as OkHttpEngine).config.proxy)
        }
    }

    @Test
    fun testBuildProxy_None() = runTest {
        every { settingsManager.getInt(Settings.PROXY_TYPE) } returns Settings.PROXY_TYPE_NONE

        httpClientBuilder.build().use { client ->
            assertEquals(Proxy.NO_PROXY, (client.engine as OkHttpEngine).config.proxy)
        }
    }

    @Test
    fun testBuildProxy_Http() = runTest {
        every { settingsManager.getInt(Settings.PROXY_TYPE) } returns Settings.PROXY_TYPE_HTTP
        every { settingsManager.getString(Settings.PROXY_HOST) } returns "proxy.example.com"
        every { settingsManager.getInt(Settings.PROXY_PORT) } returns 8080

        httpClientBuilder.build().use { client ->
            val proxy = (client.engine as OkHttpEngine).config.proxy
            assertEquals(ProxyBuilder.http(Url("http://proxy.example.com:8080")), proxy)
        }
    }

    @Test
    fun testBuildProxy_Socks() = runTest {
        every { settingsManager.getInt(Settings.PROXY_TYPE) } returns Settings.PROXY_TYPE_SOCKS
        every { settingsManager.getString(Settings.PROXY_HOST) } returns "proxy.example.com"
        every { settingsManager.getInt(Settings.PROXY_PORT) } returns 1080

        httpClientBuilder.build().use { client ->
            val proxy = (client.engine as OkHttpEngine).config.proxy
            assertEquals(ProxyBuilder.socks("proxy.example.com", 1080), proxy)
        }
    }

    @Test
    fun testCookies() = runTest {
        val url = "https://example.com/test"

        val queue = MockEngineQueue()
            // Enqueue a "cookie send" request
            .enqueue(
                headers = headers {
                    cookie(Cookie("cookie1", "1", path = "/"))
                    cookie(Cookie("cookie2", "2"))
                }
            )
            // Enqueue an expired cookie send request
            .enqueue(
                headers = headers {
                    cookie(Cookie("cookie1", "1a", path = "/", maxAge = 0))
                    cookie(Cookie("cookie2", "2a"))
                }
            )
            // Enqueue a blank default response
            .enqueue()

        httpClientBuilder.build(queue.engine).use { client ->
            // Cookies are handled by Ktor's HttpCookies plugin (AcceptAllCookiesStorage),
            // so they're stored/sent by the Ktor client.
            client.get(url)
            assertNull(queue.engine.requestHistory[0].headers[HttpHeaders.Cookie])

            // cookie should be sent with second request
            client.get(url)
            assertCookiesValues(queue.engine.requestHistory[1].headers, "cookie1" to "1", "cookie2" to "2")

            // second response let first cookie expire and overwrote second cookie
            client.get(url)
            assertCookiesValues(queue.engine.requestHistory[2].headers, "cookie2" to "2a")
        }
    }

    @Test
    fun testDefaultRequest_SetsUserAgentAndAcceptLanguage() = runTest {
        val engine = MockEngine.Default

        val origLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)

            httpClientBuilder.build(engine).use { client ->
                client.get("https://example.com/")
            }

            val request = engine.requestHistory.first()
            assertEquals(productIds.httpUserAgent, request.headers[HttpHeaders.UserAgent])
            assertEquals(
                "de-DE, de;q=0.7, *;q=0.5",
                request.headers[HttpHeaders.AcceptLanguage]
            )
        } finally {
            Locale.setDefault(origLocale)
        }
    }

    @Test
    fun testContentEncoding_HandlesIdentityEncoding() = runTest {
        val engine = MockEngine.basic(
            "Some Content",
            headers = headersOf(HttpHeaders.ContentEncoding, "identity")
        )

        httpClientBuilder.build(engine).use { client ->
            val response = client.get("https://example.com/")
            assertEquals(200, response.status.value)
            assertEquals("Some Content", response.bodyAsText())
        }
    }

    @Test
    fun testFollowRedirects_DisabledByDefault() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/") {
                respond("", HttpStatusCode.Found, headersOf("Location", "https://example.com/redirected"))
            } else {
                respond("Target", HttpStatusCode.OK)
            }
        }

        httpClientBuilder.build(engine).use { client ->
            val response = client.get("https://example.com/")
            // redirect is not followed, so we get the 302 response directly
            assertEquals(HttpStatusCode.Found, response.status)
        }
        // only the initial request was made
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun testFollowRedirects_EnabledWhenRequested() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/") {
                respond("", HttpStatusCode.Found, headersOf("Location", "https://example.com/redirected"))
            } else {
                respond("Target", HttpStatusCode.OK)
            }
        }

        httpClientBuilder.followRedirects(true).build(engine).use { client ->
            val response = client.get("https://example.com/")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Target", response.bodyAsText())
        }
        // initial request + followed redirect
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun testLogging_RedactsSensitiveHeaders() = runTest {
        val secret = "Bearer super-secret-token-12345"

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

        val engine = MockEngine.basic(headers = headersOf(HttpHeaders.SetCookie, "session=secret-cookie-value"))

        httpClientBuilder
            .logTo(logger)
            .trafficLogLevel(LogLevel.ALL)
            .build(engine).use { client ->
                client.get("https://example.com/") {
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


    /**
     * Appends a `Set-Cookie` header with the representation of [cookie].
     */
    fun HeadersBuilder.cookie(cookie: Cookie) {
        append(HttpHeaders.SetCookie, renderSetCookieHeader(cookie))
    }

    /**
     * Makes sure all the cookies in [cookies] are present in the [headers] with the expected values.
     */
    fun assertCookiesValues(headers: Headers, vararg cookies: Pair<String, String>) {
        val cookieHeader = headers[HttpHeaders.Cookie]
        assertTrue("Expected Cookie header to be present", cookieHeader != null)
        val values = cookieHeader!!.split(';').map { it.split('=', limit = 2) }
        for ((name, value) in cookies) {
            val cookieValue = values.find { it[0].trim() == name }?.getOrNull(1)?.trim()
            assertEquals(value, cookieValue)
        }
    }

}
