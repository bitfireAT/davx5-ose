/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import android.accounts.AccountManager
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AndroidAccountManagerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var androidAccountManager: AndroidAccountManager

    @Inject
    lateinit var accountManager: AccountManager

    private lateinit var account: Account

    /** Account that is currently registered (follows renames, so [tearDown] removes the right one) */
    private var currentAccount: Account? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        account = TestAccount.create(accountName = "AndroidAccountManagerTest")
        currentAccount = account
    }

    @After
    fun tearDown() {
        currentAccount?.let { TestAccount.remove(it) }
    }


    @Test
    fun testRenameAccount_Success() = runTest {
        val newName = "AndroidAccountManagerTest renamed"

        val renamed = androidAccountManager.renameAccount(account, newName)
        currentAccount = renamed

        assertEquals(newName, renamed.name)
        assertEquals(account.type, renamed.type)

        val accounts = accountManager.getAccountsByType(account.type)
        assertTrue(accounts.contains(renamed))
        assertFalse(accounts.contains(account))
    }

    @Test
    fun testRenameAccount_KeepsUserData() = runTest {
        val key = "test_key"
        accountManager.setUserData(account, key, "test_value")

        val renamed = androidAccountManager.renameAccount(account, "AndroidAccountManagerTest renamed")
        currentAccount = renamed

        assertEquals("test_value", accountManager.getUserData(renamed, key))
    }

    @Test
    fun testRenameAccount_SameName_ReturnsSameAccountWithoutRenaming() = runTest {
        val result = androidAccountManager.renameAccount(account, account.name)

        assertSame(account, result)
        assertTrue(accountManager.getAccountsByType(account.type).contains(account))
    }

    @Test
    fun testRenameAccount_BlankName_Throws() = runTest {
        for (name in listOf("", "   ")) {
            try {
                androidAccountManager.renameAccount(account, name)
                fail("Expected IllegalArgumentException for name '$name'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
        assertTrue(accountManager.getAccountsByType(account.type).contains(account))
    }

    @Test
    fun testRenameAccount_NameAlreadyExists_Throws() = runTest {
        val other = TestAccount.create(accountName = "AndroidAccountManagerTest other")
        try {
            try {
                androidAccountManager.renameAccount(account, other.name)
                fail("Expected an exception when renaming to an existing account name")
            } catch (_: Exception) {
                // expected; exact type depends on the AccountManager implementation
            }
            // original account must still exist untouched
            assertTrue(accountManager.getAccountsByType(account.type).contains(account))
        } finally {
            TestAccount.remove(other)
        }
    }

}
