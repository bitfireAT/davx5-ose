/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingMembershipsTest {

    @Test
    fun testToString_empty() =
        assertEquals("", PendingMemberships(emptySet()).toString())

    @Test
    fun testToString_single() =
        assertEquals("uid1", PendingMemberships(setOf("uid1")).toString())

    @Test
    fun testToString_multiple() {
        val result = PendingMemberships(setOf("uid1", "uid2")).toString()
        assertEquals(setOf("uid1", "uid2"), result.split('\n').toSet())
    }

    @Test
    fun testFromString_empty() =
        assertEquals(emptySet<String>(), PendingMemberships.fromString("").uids)

    @Test
    fun testFromString_single() =
        assertEquals(setOf("uid1"), PendingMemberships.fromString("uid1").uids)

    @Test
    fun testFromString_multiple() =
        assertEquals(setOf("uid1", "uid2"), PendingMemberships.fromString("uid1\nuid2").uids)

    @Test
    fun testRoundtrip() {
        val original = setOf("alice@example.com", "bob@example.com", "carol@example.com")
        assertEquals(original, PendingMemberships.fromString(PendingMemberships(original).toString()).uids)
    }

}
