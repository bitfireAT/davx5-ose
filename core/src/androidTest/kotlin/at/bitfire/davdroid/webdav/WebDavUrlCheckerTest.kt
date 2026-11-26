/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import at.bitfire.davdroid.MockEngineUtils.Default
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import io.ktor.utils.io.charsets.Charsets
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
        val result = check(MockEngine.Default, url)

        assertNull(result)
    }

    @Test
    fun getWebDavUrl_DavClass1() = runTest {
        val result = check(mockDav(collections = setOf("/"), davHeader = "1"), url)

        assertEquals(url, result)
    }

    @Test
    fun getWebDavUrl_DavClass2() = runTest {
        val result = check(mockDav(collections = setOf("/"), davHeader = "1, 2"), url)

        assertEquals(url, result)
    }

    @Test
    fun getWebDavUrl_DavClass3() = runTest {
        val result = check(mockDav(collections = setOf("/"), davHeader = "1, 3"), url)

        assertEquals(url, result)
    }

    @Test
    fun getWebDavUrl_Folder() = runTest {
        val engine = mockDav(collections = setOf("/dav/folder/"))

        val result = check(engine, Url("https://dav.example.com/dav/folder/"))

        assertEquals(Url("https://dav.example.com/dav/folder/"), result)
    }

    @Test
    fun getWebDavUrl_FolderWithoutTrailingSlash() = runTest {
        val engine = mockDav(collections = setOf("/dav/folder", "/dav/folder/"))

        val result = check(engine, Url("https://dav.example.com/dav/folder"))

        assertEquals(Url("https://dav.example.com/dav/folder/"), result)
    }

    @Test
    fun getWebDavUrl_FolderRedirected() = runTest {
        val engine = mockDav(collections = setOf("/dav/"), redirects = mapOf("/dav" to "/dav/"))

        val result = check(engine, Url("https://dav.example.com/dav"))

        assertEquals(Url("https://dav.example.com/dav/"), result)
    }

    @Test
    fun getWebDavUrl_NoResourceTypeParentNoDav() = runTest {
        val engine = mockDav(noResourceType = setOf("/dav/x"))

        val result = check(engine, Url("https://dav.example.com/dav/x"))

        assertNull(result)
    }

    @Test
    fun getWebDavUrl_File() = runTest {
        val engine = mockDav(collections = setOf("/dav/folder/"), files = setOf("/dav/folder/a%20b.txt"))

        val result = check(engine, Url("https://dav.example.com/dav/folder/a%20b.txt"))

        assertEquals(Url("https://dav.example.com/dav/folder/"), result)
    }

    @Test
    fun getWebDavUrl_FileInRoot() = runTest {
        val engine = mockDav(collections = setOf("/"), files = setOf("/file.txt"))

        val result = check(engine, Url("https://dav.example.com/file.txt"))

        assertEquals(Url("https://dav.example.com/"), result)
    }

    @Test
    fun getWebDavUrl_FileParentNoDav() = runTest {
        val engine = mockDav(files = setOf("/dav/file.txt"))

        val result = check(engine, Url("https://dav.example.com/dav/file.txt"))

        assertNull(result)
    }

    @Test
    fun getWebDavUrl_FileParentNotCollection() = runTest {
        val engine = mockDav(files = setOf("/dav/file.txt", "/dav/"))

        val result = check(engine, Url("https://dav.example.com/dav/file.txt"))

        assertNull(result)
    }


    // helpers

    private suspend fun check(engine: MockEngine, url: Url): Url? =
        engine.use {
            HttpClient(engine).use { httpClient ->
                webDavUrlChecker.checkWebDavUrl(httpClient, url)
            }
        }

    private val xmlHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())

    /** Mock WebDAV server; resources are keyed by encoded path. */
    private fun mockDav(
        collections: Set<String> = emptySet(),
        files: Set<String> = emptySet(),
        noResourceType: Set<String> = emptySet(),
        redirects: Map<String, String> = emptyMap(),
        davHeader: String = "1, 2"
    ) = MockEngine { request ->
        val path = request.url.encodedPath
        val known = path in collections || path in files || path in noResourceType
        val redirect = redirects[path]
        when {
            redirect != null ->
                respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, redirect))

            request.method == HttpMethod.Options && known ->
                respond("", HttpStatusCode.OK, headersOf("DAV", davHeader))

            request.method == HttpMethod.Options ->
                respond("", HttpStatusCode.OK)

            request.method.value == "PROPFIND" && known -> {
                val propstat = when (path) {
                    in collections -> "<propstat><prop><resourcetype><collection/></resourcetype></prop>" +
                            "<status>HTTP/1.1 200 OK</status></propstat>"
                    in files -> "<propstat><prop><resourcetype/></prop><status>HTTP/1.1 200 OK</status></propstat>"
                    else -> "<propstat><prop><resourcetype/></prop><status>HTTP/1.1 404 Not Found</status></propstat>"
                }
                val href = if (path in collections) path.trimEnd('/') + "/" else path
                respond(
                    "<multistatus xmlns='DAV:'><response><href>$href</href>$propstat</response></multistatus>",
                    HttpStatusCode.MultiStatus,
                    xmlHeaders
                )
            }

            else ->
                respond("", HttpStatusCode.NotFound)
        }
    }

}
