/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HeadResponseTest {

    private val server = MockWebServer()
    private val client = HttpClient(OkHttp)

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    private fun url() = Url(server.url("/").toString())

    @Test
    fun `strong ETag is parsed`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader(HttpHeaders.ETag, "\"abc123\""))
        assertEquals("abc123", HeadResponse.fromUrl(client, url()).eTag)
    }

    @Test
    fun `weak ETag is ignored`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader(HttpHeaders.ETag, "W/\"abc123\""))
        assertNull(HeadResponse.fromUrl(client, url()).eTag)
    }

    @Test
    fun `Last-Modified is parsed`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader(HttpHeaders.LastModified, "Wed, 21 Oct 2015 07:28:00 GMT"))
        assertNotNull(HeadResponse.fromUrl(client, url()).lastModified)
    }

    @Test
    fun `Content-Length is parsed`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.ContentLength, "12345"))
        assertEquals(12345L, HeadResponse.fromUrl(client, url()).size)
    }

    @Test
    fun `Accept-Ranges bytes enables partial content`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader(HttpHeaders.AcceptRanges, "bytes"))
        assertEquals(true, HeadResponse.fromUrl(client, url()).supportsPartial)
    }

    @Test
    fun `Accept-Ranges none disables partial content`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader(HttpHeaders.AcceptRanges, "none"))
        assertEquals(false, HeadResponse.fromUrl(client, url()).supportsPartial)
    }

}
