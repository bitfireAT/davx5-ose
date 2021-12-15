/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.xbill.DNS.DClass
import org.xbill.DNS.Name
import org.xbill.DNS.SRVRecord

class DavUtilsTest {

    val exampleURL = "http://example.com/"

    @Test
    fun testARGBtoCalDAVColor() {
        assertEquals("#00000000", DavUtils.ARGBtoCalDAVColor(0))
        assertEquals("#123456FF", DavUtils.ARGBtoCalDAVColor(0xFF123456.toInt()))
        assertEquals("#000000FF", DavUtils.ARGBtoCalDAVColor(0xFF000000.toInt()))
    }

    @Test
    fun testLastSegmentOfUrl() {
        assertEquals("/", DavUtils.lastSegmentOfUrl(exampleURL.toHttpUrl()))
        assertEquals("dir", DavUtils.lastSegmentOfUrl((exampleURL + "dir").toHttpUrl()))
        assertEquals("dir", DavUtils.lastSegmentOfUrl((exampleURL + "dir/").toHttpUrl()))
        assertEquals("file.html", DavUtils.lastSegmentOfUrl((exampleURL + "dir/file.html").toHttpUrl()))
    }

    @Test
    fun testSelectSRVRecord() {
        assertNull(DavUtils.selectSRVRecord(emptyArray()))

        val dns1010 = SRVRecord(
                Name.fromString("_caldavs._tcp.example.com."),
                DClass.IN, 3600, 10, 10, 8443, Name.fromString("dav1010.example.com.")
        )
        val dns1020 = SRVRecord(
                Name.fromString("_caldavs._tcp.example.com."),
                DClass.IN, 3600, 10, 20, 8443, Name.fromString("dav1020.example.com.")
        )
        val dns2010 = SRVRecord(
                Name.fromString("_caldavs._tcp.example.com."),
                DClass.IN, 3600, 20, 20, 8443, Name.fromString("dav2010.example.com.")
        )

        assertEquals(dns1010, DavUtils.selectSRVRecord(arrayOf(dns1010)))

        val result = IntArray(2) { 0 }
        for (i in 0 until 1000) {
            when (DavUtils.selectSRVRecord(arrayOf(dns1010, dns1020, dns2010))) {
                dns1010 -> result[0]++
                dns1020 -> result[1]++
                else -> throw AssertionError()
            }
        }
        assertTrue(result[0] > 200 && result[0] < 500)
        assertTrue(result[1] > 500 && result[1] < 800)
    }

}
