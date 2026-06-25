/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.davdroid.util.DavUtils.toUrl
import io.ktor.http.ContentType
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
    fun testUrl_LastSegment() {
        val exampleURL = "http://example.com/"
        assertEquals("/", exampleURL.toUrl().lastSegment)
        assertEquals("dir", (exampleURL + "dir").toUrl().lastSegment)
        assertEquals("dir", (exampleURL + "dir/").toUrl().lastSegment)
        assertEquals("file.html", (exampleURL + "dir/file.html").toUrl().lastSegment)
    }

}
