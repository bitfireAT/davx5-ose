/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import at.bitfire.synctools.util.Utils.capitalize
import at.bitfire.synctools.util.trimToNull
import org.junit.Assert
import org.junit.Test

class UtilsTest {

    @Test
    fun testCapitalize() {
        Assert.assertEquals("Utils Test", "utils test".capitalize()) // Test multiple words
        Assert.assertEquals("Utils", "utils".capitalize()) // Test single word
        Assert.assertEquals("", "".capitalize()) // Test empty string
    }

}