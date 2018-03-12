/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import org.junit.Assert.assertEquals
import org.junit.Test

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
        assertEquals("/", DavUtils.lastSegmentOfUrl(exampleURL))
        assertEquals("dir", DavUtils.lastSegmentOfUrl(exampleURL + "dir"))
        assertEquals("dir", DavUtils.lastSegmentOfUrl(exampleURL + "dir/"))
        assertEquals("file.html", DavUtils.lastSegmentOfUrl(exampleURL + "dir/file.html"))
    }

}
