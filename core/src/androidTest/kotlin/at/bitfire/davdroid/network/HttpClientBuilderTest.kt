/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.davdroid.ProductIds
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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

    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test
    fun testBuildKtor_CreatesWorkingClient() = runTest {
        val engine = MockEngine { respond("Some Content", HttpStatusCode.OK) }

        httpClientBuilder.get().buildKtor(engine).use { client ->
            val response = client.get("https://example.com/")
            assertEquals(200, response.status.value)
            assertEquals("Some Content", response.bodyAsText())
        }
    }

    @Test
    fun testCookies() = runTest {
        val url = "https://example.com/test"

        var callCount = 0
        val engine = MockEngine {
            callCount++
            when (callCount) {
                1 -> respond(
                    "Cookie set", HttpStatusCode.OK,
                    Headers.build {
                        append(HttpHeaders.SetCookie, "cookie1=1; path=/")
                        append(HttpHeaders.SetCookie, "cookie2=2")
                    }
                )
                2 -> respond(
                    "", HttpStatusCode.OK,
                    Headers.build {
                        append(HttpHeaders.SetCookie, "cookie1=1a; path=/; Max-Age=0")
                        append(HttpHeaders.SetCookie, "cookie2=2a")
                    }
                )
                else -> respond("", HttpStatusCode.OK)
            }
        }

        httpClientBuilder.get().buildKtor(engine).use { client ->
            // Cookies are handled by Ktor's HttpCookies plugin (AcceptAllCookiesStorage),
            // so they're stored/sent by the Ktor client.
            client.get(url)
            assertNull(engine.requestHistory[0].headers[HttpHeaders.Cookie])

            // cookie should be sent with second request
            client.get(url)
            val header = engine.requestHistory[1].headers[HttpHeaders.Cookie]
            assertTrue(header == "cookie1=1; cookie2=2" || header == "cookie2=2; cookie1=1")

            // second response let first cookie expire and overwrote second cookie
            client.get(url)
            assertEquals("cookie2=2a", engine.requestHistory[2].headers[HttpHeaders.Cookie])
        }
    }

    @Test
    fun testDefaultRequest_SetsUserAgentAndAcceptLanguage() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }

        httpClientBuilder.get().buildKtor(engine).use { client ->
            client.get("https://example.com/")
        }

        val request = engine.requestHistory.first()
        assertEquals(productIds.httpUserAgent, request.headers[HttpHeaders.UserAgent])
        val acceptLanguage = request.headers[HttpHeaders.AcceptLanguage]
        assertNotNull(acceptLanguage)
        assertTrue(acceptLanguage!!.contains("q=0.7"))
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

        httpClientBuilder.get().buildKtor(engine).use { client ->
            val response = client.get("https://example.com/")
            // redirect is not followed, so we get the 302 response directly
            assertEquals(302, response.status.value)
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

        httpClientBuilder.get().followRedirects(true).buildKtor(engine).use { client ->
            val response = client.get("https://example.com/")
            assertEquals(200, response.status.value)
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

        val engine = MockEngine {
            respond("OK", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "session=secret-cookie-value"))
        }

        httpClientBuilder.get()
            .setLogger(logger)
            .loggerInterceptorLevel(LogLevel.ALL)
            .buildKtor(engine).use { client ->
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

}
