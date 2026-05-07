/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.os.ParcelFileDescriptor
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.network.HttpClientBuilder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class StreamingFileDescriptorTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var httpClientBuilder: HttpClientBuilder

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var logger: Logger

    private lateinit var server: MockWebServer
    private lateinit var client: HttpClient
    private lateinit var scope: CoroutineScope

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

    /** Creates a [StreamingFileDescriptor] pointing at the mock server's root URL. */
    private fun create(onFinished: StreamingFileDescriptor.OnSuccessCallback): StreamingFileDescriptor {
        val url = Url(server.url("/").toString())
        return StreamingFileDescriptor(client, url, mimeType = null, scope, onFinished, logger)
    }

    @Test
    fun downloadWritesServerBodyToPipe() {
        val body = "Hello WebDAV"
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(body))

        val latch = CountDownLatch(1)
        var success = false
        val readFd = create(onFinished = { _, s -> success = s; latch.countDown() }).download()

        val received = ParcelFileDescriptor.AutoCloseInputStream(readFd).readBytes()
        assertTrue("callback not called in time", latch.await(5, TimeUnit.SECONDS))

        assertTrue(success)
        assertArrayEquals(body.toByteArray(), received)
    }

    @Test
    fun uploadSendsPipeContentToServer() {
        server.enqueue(MockResponse()
            .setResponseCode(201))
        val body = "Uploaded content".toByteArray()

        val latch = CountDownLatch(1)
        val writeFd = create(onFinished = { _, _ -> latch.countDown() }).upload()
        ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { it.write(body) }

        assertTrue("callback not called in time", latch.await(5, TimeUnit.SECONDS))
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("PUT", request!!.method)
        assertArrayEquals(body, request.body.readByteArray())
    }

    @Test
    fun downloadClosesPipeWithErrorOnHttpError() {
        server.enqueue(MockResponse()
            .setResponseCode(404))

        val latch = CountDownLatch(1)
        var success = true
        val readFd = create(onFinished = { _, s -> success = s; latch.countDown() }).download()

        try {
            ParcelFileDescriptor.AutoCloseInputStream(readFd).readBytes()
        } catch (_: Exception) { /* pipe may be closed with error */ }

        assertTrue("callback not called in time", latch.await(5, TimeUnit.SECONDS))
        assertFalse(success)
    }

}
