/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StringUtilsTest {

    @Test
    fun trimToNull_Empty() {
        assertNull("".trimToNull())
    }

    @Test
    fun trimToNull_NoWhitespace() {
        assertEquals("test", "test".trimToNull())
    }

    @Test
    fun trimToNull_Null() {
        assertNull(null.trimToNull())
    }

    @Test
    fun trimToNull_PaddedWithWhitespace() {
        assertEquals("test", "\r\n  test  ".trimToNull())
    }

}