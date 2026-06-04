/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StringUtilsTest {

    @Test
    fun withTrailingSlash_WithSlash() {
        assertEquals("test/", "test/".withTrailingSlash())
    }

    @Test
    fun withTrailingSlash_WithoutSlash() {
        assertEquals("test/", "test".withTrailingSlash())
    }

}