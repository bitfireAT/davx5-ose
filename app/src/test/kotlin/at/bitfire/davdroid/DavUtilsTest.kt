/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import at.bitfire.davdroid.util.DavUtils
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.davdroid.util.DavUtils.parent
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test

class DavUtilsTest {

    @Test
    fun testAcceptAnything() {
        assertEquals("*/*", DavUtils.acceptAnything(null))
        assertEquals("some/thing;v=2.1, */*;q=0.8", DavUtils.acceptAnything("some/thing;v=2.1".toMediaType()))
    }

    @Test
    fun testARGBtoCalDAVColor() {
        assertEquals("#00000000", DavUtils.ARGBtoCalDAVColor(0))
        assertEquals("#123456FF", DavUtils.ARGBtoCalDAVColor(0xFF123456.toInt()))
        assertEquals("#000000FF", DavUtils.ARGBtoCalDAVColor(0xFF000000.toInt()))
    }


    @Test
    fun testHttpUrl_LastSegment() {
        val exampleURL = "http://example.com/"
        Assert.assertEquals("/", exampleURL.toHttpUrl().lastSegment)
        Assert.assertEquals("dir", (exampleURL + "dir").toHttpUrl().lastSegment)
        Assert.assertEquals("dir", (exampleURL + "dir/").toHttpUrl().lastSegment)
        Assert.assertEquals("file.html", (exampleURL + "dir/file.html").toHttpUrl().lastSegment)
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
