/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class WebDavMountRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: WebDavMountRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    val web = MockWebServer()
    val url = web.url("/")

    @Test
    fun testHasWebDav_NoDavHeader() = runTest {
        web.enqueue(MockResponse().setResponseCode(200))
        assertNull(repository.hasWebDav(url, null))
    }

    @Test
    fun testHasWebDav_DavClass1() = runTest {
        web.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("DAV: 1"))
        assertEquals(url, repository.hasWebDav(url, null))
    }

    @Test
    fun testHasWebDav_DavClass2() = runTest {
        web.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("DAV: 1, 2"))
        assertEquals(url,repository.hasWebDav(url, null))
    }

    @Test
    fun testHasWebDav_DavClass3() = runTest {
        web.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("DAV: 1, 3"))
        assertEquals(url,repository.hasWebDav(url, null))
    }

}