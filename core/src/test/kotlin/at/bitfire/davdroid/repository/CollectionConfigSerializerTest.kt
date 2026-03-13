/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import at.bitfire.davdroid.db.Collection
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionConfigSerializerTest {

    @Test
    fun `export and parse round-trip`() {
        val collections = listOf(
            Collection(
                type = "CALENDAR",
                url = "https://example.com/cal/personal/".toHttpUrl(),
                sync = true,
                forceReadOnly = false
            ),
            Collection(
                type = "ADDRESS_BOOK",
                url = "https://example.com/contacts/default/".toHttpUrl(),
                sync = false,
                forceReadOnly = true
            )
        )

        val json = CollectionConfigSerializer.export(
            accountName = "user@example.com",
            collections = collections,
            baseUrl = "https://example.com/webdav/"
        )
        val parsed = CollectionConfigSerializer.parse(json)

        assertEquals(1, parsed.version)
        assertEquals("user@example.com", parsed.accountName)
        assertEquals("https://example.com/webdav/", parsed.baseUrl)
        assertNull(parsed.credentials)
        assertEquals(2, parsed.collections.size)

        val cal = parsed.collections[0]
        assertEquals("https://example.com/cal/personal/", cal.url)
        assertEquals("CALENDAR", cal.type)
        assertTrue(cal.sync)
        assertEquals(false, cal.forceReadOnly)

        val contacts = parsed.collections[1]
        assertEquals("https://example.com/contacts/default/", contacts.url)
        assertEquals("ADDRESS_BOOK", contacts.type)
        assertEquals(false, contacts.sync)
        assertTrue(contacts.forceReadOnly)
    }

    @Test
    fun `export with credentials`() {
        val json = CollectionConfigSerializer.export(
            accountName = "user@example.com",
            collections = emptyList(),
            credentials = AccountCredentials(username = "user", password = "secret"),
            baseUrl = "https://example.com/"
        )
        val parsed = CollectionConfigSerializer.parse(json)

        assertNotNull(parsed.credentials)
        assertEquals("user", parsed.credentials?.username)
        assertEquals("secret", parsed.credentials?.password)
    }

    @Test
    fun `parse ignores unknown keys`() {
        val json = """
            {
                "version": 1,
                "accountName": "test@example.com",
                "unknownField": "should be ignored",
                "collections": []
            }
        """.trimIndent()
        val parsed = CollectionConfigSerializer.parse(json)
        assertEquals("test@example.com", parsed.accountName)
        assertTrue(parsed.collections.isEmpty())
    }

    @Test
    fun `parse minimal config without optional fields`() {
        val json = """
            {
                "version": 1,
                "accountName": "test@example.com",
                "collections": []
            }
        """.trimIndent()
        val parsed = CollectionConfigSerializer.parse(json)

        assertEquals(1, parsed.version)
        assertEquals("test@example.com", parsed.accountName)
        assertNull(parsed.baseUrl)
        assertNull(parsed.credentials)
        assertTrue(parsed.collections.isEmpty())
    }

    @Test
    fun `parse full config with credentials and collections`() {
        val json = """
            {
                "version": 1,
                "accountName": "user@example.com",
                "baseUrl": "https://dav.example.com/webdav/user%40example.com/",
                "credentials": {
                    "username": "user@example.com",
                    "password": "testpass"
                },
                "collections": [
                    {
                        "url": "https://dav.example.com/webdav/user%40example.com/cal/",
                        "type": "CALENDAR",
                        "sync": true,
                        "forceReadOnly": false
                    }
                ]
            }
        """.trimIndent()
        val parsed = CollectionConfigSerializer.parse(json)

        assertEquals("user@example.com", parsed.accountName)
        assertEquals("https://dav.example.com/webdav/user%40example.com/", parsed.baseUrl)
        assertEquals("user@example.com", parsed.credentials?.username)
        assertEquals(1, parsed.collections.size)
        assertEquals("CALENDAR", parsed.collections[0].type)
        assertTrue(parsed.collections[0].sync)
    }
}
