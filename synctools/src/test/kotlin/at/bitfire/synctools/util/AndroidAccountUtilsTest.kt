/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AuthenticatorDescription
import android.content.Context
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

@RunWith(RobolectricTestRunner::class)
class AndroidAccountUtilsTest {

    val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun testCreateAccount() {
        val userData = mapOf(
            "int" to "1",
            "string" to "abc/\"-"
        )

        val account = Account("testCreateAccount", javaClass.name)
        val manager = AccountManager.get(context)
        try {
            assertTrue(AndroidAccountUtils.createAccount(context, account, userData, "secret".toSensitiveString()))

            // validate user data
            assertEquals("1", manager.getUserData(account, "int"))
            assertEquals("abc/\"-", manager.getUserData(account, "string"))
            assertEquals("secret", manager.getPassword(account))
        } finally {
            assertTrue(manager.removeAccountExplicitly(account))
        }
    }

    @Test
    fun testCreateAccountDenied() {
        val account = Account("private-account", javaClass.name)
        val manager = mockk<AccountManager>()
        val context = mockk<Context> {
            every { getSystemService(Context.ACCOUNT_SERVICE) } returns manager
        }
        val denied = SecurityException("Account creation denied by Android")
        val records = mutableListOf<LogRecord>()
        val logger = Logger.getLogger(AndroidAccountUtils::class.java.name)
        val handler = object : Handler() {
            override fun publish(record: LogRecord) { records += record }
            override fun flush() {}
            override fun close() {}
        }
        logger.addHandler(handler)
        try {
            every { manager.addAccountExplicitly(any(), any(), any()) } throws denied
            val provider = AuthenticatorDescription(account.type, "matching.provider", 0, 0, 0, 0)
            val unrelated = AuthenticatorDescription("other.type", "unrelated.provider", 0, 0, 0, 0)
            for (diagnostic in listOf("matching.provider", "unknown", "unavailable")) {
                records.clear()
                when (diagnostic) {
                    "matching.provider" -> every { manager.authenticatorTypes } returns arrayOf(provider, unrelated)
                    "unknown" -> every { manager.authenticatorTypes } returns emptyArray()
                    else -> every { manager.authenticatorTypes } throws IllegalStateException("Lookup failed")
                }

                assertFalse(AndroidAccountUtils.createAccount(context, account,
                    mapOf("private-key" to "private-value"), "private-password".toSensitiveString()))

                assertTrue(records.any { it.thrown === denied })
                assertTrue(records.any { diagnostic in it.message })
                assertFalse(records.any { "unrelated.provider" in it.message || "private-" in it.message })
            }

            records.clear()
            every { manager.addAccountExplicitly(any(), any(), any()) } returns false
            assertFalse(AndroidAccountUtils.createAccount(context, account, mapOf("key" to "value")))
            assertTrue(records.isEmpty())
            verify(exactly = 3) { manager.authenticatorTypes }
            verify(exactly = 0) { manager.setUserData(any(), any(), any()) }
            verify(exactly = 0) { manager.getUserData(any(), any()) }
        } finally {
            logger.removeHandler(handler)
        }
    }

}
