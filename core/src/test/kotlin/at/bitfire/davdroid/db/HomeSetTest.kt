/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import at.bitfire.davdroid.util.DavUtils.toUrl
import at.bitfire.synctools.test.assertThrows
import io.ktor.http.Url
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSetTest {

    @Test
    fun `url ending with slash`() {
        val url = "https://domain.example/collection/".toUrl()

        val homeSet = createHomeSet(url = url)

        assertEquals(url, homeSet.url)
    }

    @Test
    fun `url without trailing slash should throw`() {
        assertThrows(
            IllegalArgumentException(
                "Not a collection URL (path does not end with a slash): https://domain.example/collection/file"
            )
        ) {
            createHomeSet(
                url = "https://domain.example/collection/file".toUrl()
            )
        }
    }

    @Test
    fun `url with empty path should throw`() {
        assertThrows(
            IllegalArgumentException("Not a collection URL (path does not end with a slash): https://domain.example")
        ) {
            createHomeSet(
                url = "https://domain.example".toUrl()
            )
        }
    }

    @Test
    fun `title() uses displayName`() {
        val displayName = "Display Name"
        val url = "https://domain.example/collection/".toUrl()
        val homeSet = createHomeSet(url = url, displayName = displayName)

        val title = homeSet.title()

        assertEquals(displayName, title)
    }

    @Test
    fun `title() uses collection name when displayName is null`() {
        val url = "https://domain.example/collection/".toUrl()
        val homeSet = createHomeSet(url = url, displayName = null)

        val title = homeSet.title()

        assertEquals("collection", title)
    }

    private fun createHomeSet(url: Url, displayName: String? = null): HomeSet {
        return HomeSet(
            id = 1,
            serviceId = 1,
            personal = true,
            url = url,
            displayName = displayName
        )
    }
}
