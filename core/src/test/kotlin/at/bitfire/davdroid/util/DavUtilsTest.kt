/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.davdroid.util.DavUtils.parent
import at.bitfire.davdroid.util.DavUtils.resolve
import io.ktor.http.ContentType
import io.ktor.http.Url
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class DavUtilsTest {

    @Test
    fun testAcceptAnything() {
        assertEquals("*/*", DavUtils.acceptAnything(null))
        assertEquals("some/thing; v=2.1, */*;q=0.8", DavUtils.acceptAnything(ContentType.parse("some/thing;v=2.1")))
    }

    @Test
    fun testArgbToHexColor() {
        assertEquals("#000000", DavUtils.argbToHexColor(0))
        assertEquals("#123456", DavUtils.argbToHexColor(0xFF123456.toInt()))
        assertEquals("#123456", DavUtils.argbToHexColor(0x00123456))
        assertEquals("#000000", DavUtils.argbToHexColor(0xFF000000.toInt()))
    }

    @Test
    fun `fileNameFromUid (good uid)`() {
        assertEquals("good-uid.txt", DavUtils.fileNameFromUid("good-uid", "txt"))
    }

    @Test
    fun `fileNameFromUid (bad uid)`() {
        assertEquals("new-uuid.txt", DavUtils.fileNameFromUid("bad\\uid", "txt", generateUuid = { "new-uuid" }))
    }

    @Test
    fun `generateUidIfNecessary (existing uid)`() {
        assertEquals(
            DavUtils.UidGenerationResult("existing", generated = false),
            DavUtils.generateUidIfNecessary("existing")
        )
    }

    @Test
    fun `generateUidIfNecessary (no existing uid)`() {
        assertEquals(
            DavUtils.UidGenerationResult("new-uuid", generated = true),
            DavUtils.generateUidIfNecessary(null, generateUuid = { "new-uuid" })
        )
    }

    @Test
    fun testHttpUrl_LastSegment() {
        val exampleURL = "http://example.com/"
        assertEquals("/", exampleURL.toHttpUrl().lastSegment)
        assertEquals("dir", (exampleURL + "dir").toHttpUrl().lastSegment)
        assertEquals("dir", (exampleURL + "dir/").toHttpUrl().lastSegment)
        assertEquals("file.html", (exampleURL + "dir/file.html").toHttpUrl().lastSegment)
    }

    @Test
    fun testHttpUrl_Parent() {
        // with trailing slash
        assertEquals("http://example.com/1/2/".toHttpUrl(), "http://example.com/1/2/3/".toHttpUrl().parent())
        assertEquals("http://example.com/1/".toHttpUrl(), "http://example.com/1/2/".toHttpUrl().parent())
        assertEquals("http://example.com/".toHttpUrl(), "http://example.com/1/".toHttpUrl().parent())
        assertEquals("http://example.com/".toHttpUrl(), "http://example.com/".toHttpUrl().parent())

        // without trailing slash
        assertEquals("http://example.com/1/2/".toHttpUrl(), "http://example.com/1/2/3".toHttpUrl().parent())
        assertEquals("http://example.com/1/".toHttpUrl(), "http://example.com/1/2".toHttpUrl().parent())
        assertEquals("http://example.com/".toHttpUrl(), "http://example.com/1".toHttpUrl().parent())
        assertEquals("http://example.com/".toHttpUrl(), "http://example.com".toHttpUrl().parent())
    }

    @Test
    fun testUrl_Resolve_Collection() {
        val collection = Url("https://example.com/base/path/")

        // relative path
        assertEquals(Url("https://example.com/base/path/relative"), collection.resolve("relative"))
        assertEquals(Url("https://example.com/base/path/subdir/file"), collection.resolve("subdir/file"))

        // absolute path
        assertEquals(Url("https://example.com/absolute"), collection.resolve("/absolute"))
        assertEquals(Url("https://example.com/"), collection.resolve("/"))

        // absolute URL
        assertEquals(Url("https://other.com/path"), collection.resolve("https://other.com/path"))
        assertEquals(Url("http://example.org/test"), collection.resolve("http://example.org/test"))
    }

    @Test
    fun testUrl_Resolve_NonCollection() {
        val baseUrl = Url("https://example.com/base")

        // relative path
        assertEquals(Url("https://example.com/relative"), baseUrl.resolve("relative"))
        assertEquals(Url("https://example.com/subdir/file"), baseUrl.resolve("subdir/file"))

        // absolute path
        assertEquals(Url("https://example.com/absolute"), baseUrl.resolve("/absolute"))
        assertEquals(Url("https://example.com/"), baseUrl.resolve("/"))

        // absolute URL
        assertEquals(Url("https://other.com/path"), baseUrl.resolve("https://other.com/path"))
        assertEquals(Url("http://example.org/test"), baseUrl.resolve("http://example.org/test"))
    }

}
