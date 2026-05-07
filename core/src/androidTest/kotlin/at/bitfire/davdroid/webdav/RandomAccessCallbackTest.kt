/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.content.Context
import android.system.ErrnoException
import android.system.OsConstants
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.network.HttpClientBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class RandomAccessCallbackTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var httpClientBuilder: HttpClientBuilder

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var logger: Logger

    private lateinit var server: MockWebServer
    private lateinit var client: HttpClient
    private lateinit var scope: CoroutineScope

    /** File size = exactly one page so MockWebServer serves the full page in one request. */
    private val fileSize = 64L
    private val fileContent = ByteArray(fileSize.toInt()) { it.toByte() }

    @Before
    fun setUp() {
        hiltRule.inject()

        scope = CoroutineScope(ioDispatcher + SupervisorJob())

        server = MockWebServer()
        server.start()

        client = httpClientBuilder.buildKtor()
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
        scope.cancel()
    }

    private fun url() = Url(server.url("/").toString())

    private fun makeCallback() = RandomAccessCallback(
        httpClient = client,
        url = url(),
        mimeType = null,
        headResponse = HeadResponse(size = fileSize, eTag = "testETag"),
        externalScope = scope,
        context = context,
        logger = logger
    )

    @Test
    fun rangedRequestReturnsCorrectBytes() {
        // Server responds with the full page (offset 0, size = fileSize)
        server.enqueue(MockResponse()
            .setResponseCode(206)
            .setBody(okio.Buffer().write(fileContent))
            .addHeader(HttpHeaders.ContentRange, "bytes 0-${fileSize - 1}/$fileSize"))

        val data = ByteArray(32)
        makeCallback().onRead(0, 32, data)

        assertArrayEquals(fileContent.copyOf(32), data)
    }

    @Test
    fun response200CausesErrnoExceptionWithEio() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("full body"))

        try {
            makeCallback().onRead(0, 32, ByteArray(32))
            fail("Expected ErrnoException")
        } catch (e: ErrnoException) {
            // Guava LoadingCache wraps loader exceptions in ExecutionException,
            // which doesn't match any specific branch → EIO
            assertEquals(OsConstants.EIO, e.errno)
        }
    }

}
