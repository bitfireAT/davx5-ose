/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import at.bitfire.davdroid.util.DavUtils.toUrl
import io.ktor.http.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun testUrl_extractCollectionName() {
        assertEquals("/", DavUtils.extractCollectionName("https://domain.example".toUrl()))
        assertEquals("/", DavUtils.extractCollectionName("https://domain.example/".toUrl()))
        assertEquals("", DavUtils.extractCollectionName("https://domain.example//".toUrl()))
        assertEquals("collection", DavUtils.extractCollectionName("https://domain.example/collection/".toUrl()))
        assertEquals("collection", DavUtils.extractCollectionName("https://domain.example/path/collection/".toUrl()))
        assertEquals("decoded", DavUtils.extractCollectionName("https://domain.example/decode%64/".toUrl()))

        assertThrows(IllegalArgumentException::class.java) {
            DavUtils.extractCollectionName("https://domain.example/file".toUrl())
        }
        assertThrows(IllegalArgumentException::class.java) {
            DavUtils.extractCollectionName("https://domain.example/collection/file".toUrl())
        }
    }

    @Test
    fun testUrl_extractFileName() {
        assertEquals("file.ext", DavUtils.extractFileName("https://domain.example/file.ext".toUrl()))
        assertEquals("file", DavUtils.extractFileName("https://domain.example/collection/file".toUrl()))
        assertEquals("file", DavUtils.extractFileName("https://domain.example/path/collection/file".toUrl()))

        // Note: We probably want to return the encoded name in the future.
        assertEquals("decoded", DavUtils.extractFileName("https://domain.example/decode%64".toUrl()))
        assertEquals("A/B", DavUtils.extractFileName("https://domain.example/A%2FB".toUrl()))
        assertEquals("/", DavUtils.extractFileName("https://domain.example/%2F".toUrl()))

        assertThrows(IllegalArgumentException::class.java) {
            DavUtils.extractFileName("https://domain.example".toUrl())
        }
        assertThrows(IllegalArgumentException::class.java) {
            DavUtils.extractFileName("https://domain.example/".toUrl())
        }
        assertThrows(IllegalArgumentException::class.java) {
            DavUtils.extractFileName("https://domain.example/path/".toUrl())
        }
    }

}
