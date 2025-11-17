/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.davdroid.util.DavUtils.parent
import io.ktor.http.ContentType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class DavUtilsTest {

    @Test
    fun testAcceptAnything() {
        assertEquals("*/*", DavUtils.acceptAnything(null))
        assertEquals("some/thing;v=2.1, */*;q=0.8", DavUtils.acceptAnything(ContentType.parse("some/thing;v=2.1")))
    }

    @Test
    fun testARGBtoCalDAVColor() {
        assertEquals("#00000000", DavUtils.ARGBtoCalDAVColor(0))
        assertEquals("#123456FF", DavUtils.ARGBtoCalDAVColor(0xFF123456.toInt()))
        assertEquals("#000000FF", DavUtils.ARGBtoCalDAVColor(0xFF000000.toInt()))
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

}