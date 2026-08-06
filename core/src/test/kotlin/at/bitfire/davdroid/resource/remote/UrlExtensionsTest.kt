/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import io.ktor.http.Url
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlExtensionsTest {

    @Test
    fun `member() appends the file name as a path segment`() {
        val url = Url("https://example.com/dav/")
        assertEquals(Url("https://example.com/dav/some-file.ics"), url.member("some-file.ics"))
    }

    @Test
    fun `member() encodes a literal slash in the file name`() {
        val url = Url("https://example.com/dav/")
        assertEquals("/dav/has%2Fslash.ics", url.member("has/slash.ics").encodedPath)
    }

}
