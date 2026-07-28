/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import at.bitfire.synctools.util.Utils.containsIgnoreCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilsTest {

    @Test
    fun `containsIgnoreCase with exact case match`() {
        assertTrue(setOf("TRIGGER").containsIgnoreCase("TRIGGER"))
    }

    @Test
    fun `containsIgnoreCase with lowercase value`() {
        assertTrue(setOf("TRIGGER").containsIgnoreCase("trigger"))
    }

    @Test
    fun `containsIgnoreCase with mixed-case value`() {
        assertTrue(setOf("TRIGGER").containsIgnoreCase("Trigger"))
    }

    @Test
    fun `containsIgnoreCase with no match`() {
        assertFalse(setOf("TRIGGER").containsIgnoreCase("ACTION"))
    }

    @Test
    fun `containsIgnoreCase with empty set`() {
        assertFalse(emptySet<String>().containsIgnoreCase("TRIGGER"))
    }

}
