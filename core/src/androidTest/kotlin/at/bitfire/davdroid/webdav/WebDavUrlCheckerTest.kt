/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import at.bitfire.davdroid.MockEngineUtils.Default
import at.bitfire.davdroid.MockEngineUtils.basic
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.Url
import io.ktor.http.headersOf
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class WebDavUrlCheckerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var webDavUrlChecker: WebDavUrlChecker

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private val url = Url("https://dav.example.com/")

    @Test
    fun getWebDavUrl_NoDavHeader() = runTest {
        val engine = MockEngine.Default
        val result = webDavUrlChecker.checkWebDavUrl(HttpClient(engine), url)
        assertNull(result)
    }

    @Test
    fun getWebDavUrl_DavClass1() = runTest {
        val engine = MockEngine.basic(headers = headersOf("DAV", "1"))
        val result = webDavUrlChecker.checkWebDavUrl(HttpClient(engine), url)
        assertEquals(url, result)
    }

    @Test
    fun getWebDavUrl_DavClass2() = runTest {
        val engine = MockEngine.basic(headers = headersOf("DAV", "1, 2"))
        val result = webDavUrlChecker.checkWebDavUrl(HttpClient(engine), url)
        assertEquals(url, result)
    }

    @Test
    fun getWebDavUrl_DavClass3() = runTest {
        val engine = MockEngine.basic(headers = headersOf("DAV", "1, 3"))
        val result = webDavUrlChecker.checkWebDavUrl(HttpClient(engine), url)
        assertEquals(url, result)
    }
}
