/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThrowableUtilsTest {

    @Test
    fun `causedBy() matches the receiver itself`() {
        val exception = IllegalStateException("boom")

        val result = exception.causedBy<IllegalStateException>()

        assertEquals(exception, result)
    }

    @Test
    fun `causedBy() matches one level down`() {
        val cause = IllegalStateException("boom")
        val exception = RuntimeException("wrapper", cause)

        val result = exception.causedBy<IllegalStateException>()

        assertEquals(cause, result)
    }

    @Test
    fun `causedBy() matches several levels down`() {
        val cause = IllegalStateException("boom")
        val exception = RuntimeException("outer", RuntimeException("inner", cause))

        val result = exception.causedBy<IllegalStateException>()

        assertEquals(cause, result)
    }

    @Test
    fun `causedBy() returns null when nothing matches`() {
        val exception = RuntimeException("outer", RuntimeException("inner"))

        val result = exception.causedBy<IllegalStateException>()

        assertNull(result)
    }

}
