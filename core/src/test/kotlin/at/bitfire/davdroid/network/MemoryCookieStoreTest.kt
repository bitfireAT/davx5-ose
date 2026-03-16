/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test

class MemoryCookieStoreTest {

    lateinit var store: MemoryCookieStore

    @Before
    fun setup() {
        store = MemoryCookieStore()
    }


    @Test
    fun testSaveFromResponse_AndRead() {
        val url = "https://example.com/path".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("cookie1")
            .value("value1")
            .domain("example.com")
            .path("/path")
            .build()
        store.saveFromResponse(url, listOf(cookie))
        assertArrayEquals(
            arrayOf(cookie),
            store.loadForRequest(url).toTypedArray()
        )
    }

    @Test
    fun testSaveFromResponse_Overwrite_AndRead() {
        val url = "https://example.com/path".toHttpUrl()
        store.saveFromResponse(url, listOf(
            Cookie.Builder()
                .name("cookie1")
                .value("first value")
                .domain("example.com")
                .path("/path")
                .build()
        ))

        val updatedCookie = Cookie.Builder()
            .name("cookie1")
            .value("updated value")
            .domain("example.com")
            .path("/path")
            .build()
        store.saveFromResponse(url, listOf(updatedCookie))
        assertArrayEquals(
            arrayOf(updatedCookie),
            store.loadForRequest(url).toTypedArray()
        )
    }


    @Test
    fun testLoadForRequest_SubPath() {
        val url = "https://example.com/path".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("cookie1")
            .value("value1")
            .domain("example.com")
            .path("/path")
            .build()
        store.saveFromResponse(url, listOf(cookie))

        assertArrayEquals(
            arrayOf(cookie),
            store.loadForRequest(url.newBuilder()
                .addPathSegment("sub-path")
                .build()).toTypedArray()
        )
    }

}