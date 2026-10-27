/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import at.bitfire.davdroid.util.DavUtils.toUrl
import at.bitfire.synctools.test.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionTest {

    @Test
    fun `url ending with slash`() {
        val url = "https://domain.example/collection/".toUrl()

        val collection = Collection(type = Collection.TYPE_CALENDAR, url = url)

        assertEquals(url, collection.url)
    }

    @Test
    fun `url without trailing slash should throw`() {
        assertThrows(
            IllegalArgumentException(
                "Not a collection URL (path does not end with a slash): https://domain.example/collection/file"
            )
        ) {
            Collection(
                type = Collection.TYPE_CALENDAR,
                url = "https://domain.example/collection/file".toUrl()
            )
        }
    }

    @Test
    fun `url with empty path should throw`() {
        assertThrows(
            IllegalArgumentException("Not a collection URL (path does not end with a slash): https://domain.example")
        ) {
            Collection(
                type = Collection.TYPE_CALENDAR,
                url = "https://domain.example".toUrl()
            )
        }
    }

    @Test
    fun `title() uses displayName`() {
        val displayName = "Display Name"
        val url = "https://domain.example/collection/".toUrl()
        val collection = Collection(type = Collection.TYPE_CALENDAR, url = url, displayName = displayName)

        val title = collection.title()

        assertEquals(displayName, title)
    }

    @Test
    fun `title() uses collection name when displayName is null`() {
        val url = "https://domain.example/collection/".toUrl()
        val collection = Collection(type = Collection.TYPE_CALENDAR, url = url, displayName = null)

        val title = collection.title()

        assertEquals("collection", title)
    }
}
