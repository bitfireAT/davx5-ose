/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.util

import at.bitfire.synctools.util.Utils.capitalize
import at.bitfire.synctools.util.Utils.trimToNull
import org.junit.Assert
import org.junit.Test

class UtilsTest {
    @Test
    fun testCapitalize() {
        Assert.assertEquals("Utils Test", "utils test".capitalize()) // Test multiple words
        Assert.assertEquals("Utils", "utils".capitalize()) // Test single word
        Assert.assertEquals("", "".capitalize()) // Test empty string
    }

    @Test
    fun testTrimToNull() {
        Assert.assertEquals("test", "  test".trimToNull()) // Test spaces only before
        Assert.assertEquals("test", "test  ".trimToNull()) // Test spaces only after
        Assert.assertEquals("test", "  test  ".trimToNull()) // Test spaces before and after
        Assert.assertNull("     ".trimToNull()) // Test spaces
        Assert.assertNull("".trimToNull()) // Test empty string
    }
}