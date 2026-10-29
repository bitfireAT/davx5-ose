/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AuthenticatorDescription
import android.content.Context
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import java.util.logging.Level
import java.util.logging.Logger

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)      // required because main project uses Conscrypt, but unit tests do not
class AndroidAccountManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val logger = mockk<Logger>(relaxed = true)

    private val account = Account("Test Account", javaClass.name)
    private val denied = SecurityException("Account creation denied by Android")

    private fun androidAccountManager(manager: AccountManager) = AndroidAccountManager(
        accountManager = { manager },
        context = context,
        database = mockk(relaxed = true),
        ioDispatcher = Dispatchers.IO,
        logger = logger
    )


    @Test
    fun `createAccount() creates account with user data and password`() {
        val manager = AccountManager.get(context)
        val userData = mapOf(
            "int" to "1",
            "string" to "abc/\"-"
        )

        try {
            val created = androidAccountManager(manager).createAccount(account, userData, "secret".toSensitiveString())

            assertTrue(created)
            assertEquals("1", manager.getUserData(account, "int"))
            assertEquals("abc/\"-", manager.getUserData(account, "string"))
            assertEquals("secret", manager.getPassword(account))
        } finally {
            assertTrue(manager.removeAccountExplicitly(account))
        }
    }

    @Test
    fun `createAccount() logs matching account providers when denied`() {
        val manager = mockk<AccountManager> {
            every { addAccountExplicitly(any(), any(), any()) } throws denied
            every { authenticatorTypes } returns arrayOf(
                AuthenticatorDescription(account.type, "matching.provider", 0, 0, 0, 0),
                AuthenticatorDescription("other.type", "unrelated.provider", 0, 0, 0, 0)
            )
        }

        val created = androidAccountManager(manager).createAccount(account)

        assertFalse(created)
        verify {
            logger.log(Level.WARNING, any<String>(), denied)
            logger.warning(match<String> { "matching.provider" in it && "unrelated.provider" !in it })
        }
    }

    @Test
    fun `createAccount() logs unknown account provider when denied and none visible`() {
        val manager = mockk<AccountManager> {
            every { addAccountExplicitly(any(), any(), any()) } throws denied
            every { authenticatorTypes } returns emptyArray()
        }

        val created = androidAccountManager(manager).createAccount(account)

        assertFalse(created)
        verify {
            logger.warning(match<String> { "unknown" in it })
        }
    }

    @Test
    fun `createAccount() logs failed account provider lookup when denied`() {
        val lookupFailed = IllegalStateException("Lookup failed")
        val manager = mockk<AccountManager> {
            every { addAccountExplicitly(any(), any(), any()) } throws denied
            every { authenticatorTypes } throws lookupFailed
        }

        val created = androidAccountManager(manager).createAccount(account)

        assertFalse(created)
        verify {
            logger.log(Level.WARNING, any<String>(), lookupFailed)
        }
    }

    @Test
    fun `createAccount() returns false without logging when account is not created`() {
        val manager = mockk<AccountManager> {
            every { addAccountExplicitly(any(), any(), any()) } returns false
        }

        val created = androidAccountManager(manager).createAccount(account)

        assertFalse(created)
        verify(exactly = 0) {
            manager.authenticatorTypes
            logger.log(any(), any<String>(), any<Throwable>())
            logger.warning(any<String>())
        }
    }

}
