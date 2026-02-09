/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import at.bitfire.davdroid.util.SensitiveString.Companion.toSensitiveString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveStringTest {

    private data class UsernameAndPassword(
        val username: String,
        val password: SensitiveString
    )

    @Test
    fun `equals (other object)`() {
        val password = "some-password".toSensitiveString()
        assertFalse(password == Any())
    }

    @Test
    fun `equals (other password)`() {
        val password = "some-password".toSensitiveString()
        assertFalse(password == "other-password".toSensitiveString())
    }

    @Test
    fun `equals (same password)`() {
        val password = "some-password".toSensitiveString()
        assertTrue(password == "some-password".toSensitiveString())
    }

    @Test
    fun `toString in data class`() {
        val credentials = UsernameAndPassword(
            "some-user",
            "some-password".toSensitiveString()
        )

        val logMessage = "Credentials: $credentials"
        assertEquals("Credentials: UsernameAndPassword(username=some-user, password=*****)", logMessage)
    }

}