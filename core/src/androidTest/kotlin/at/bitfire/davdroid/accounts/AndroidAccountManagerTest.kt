/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import android.accounts.AccountManager
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun testRenameWithData_Success() = runTest {
        val newName = "AndroidAccountManagerTest renamed"

        val renamed = androidAccountManager.renameWithData(account, newName) { }
        currentAccount = renamed

        assertEquals(newName, renamed.name)
        assertEquals(account.type, renamed.type)

        val accounts = accountManager.getAccountsByType(account.type)
        assertTrue(accounts.contains(renamed))
        assertFalse(accounts.contains(account))
    }

    @Test
    fun testRenameWithData_KeepsUserData() = runTest {
        // use a key from userDataKeys so it gets copied
        accountManager.setUserData(account, AccountSettings.KEY_WIFI_ONLY, "1")

        val renamed = androidAccountManager.renameWithData(account, "AndroidAccountManagerTest renamed") { }
        currentAccount = renamed

        assertEquals("1", accountManager.getUserData(renamed, AccountSettings.KEY_WIFI_ONLY))
    }

    @Test
    fun testRenameWithData_DoesNotCopyUnknownUserData() = runTest {
        accountManager.setUserData(account, "unknown_key", "value")

        val renamed = androidAccountManager.renameWithData(account, "AndroidAccountManagerTest renamed") { }
        currentAccount = renamed

        assertNull(accountManager.getUserData(renamed, "unknown_key"))
    }

    @Test
    fun testRenameWithData_KeepsPassword() = runTest {
        accountManager.setPassword(account, "secret")

        val renamed = androidAccountManager.renameWithData(account, "AndroidAccountManagerTest renamed") { }
        currentAccount = renamed

        assertEquals("secret", accountManager.getPassword(renamed))
    }

    @Test
    fun testRenameWithData_CallsOnUpdateBeforeRemovingOldAccount() = runTest {
        var oldAccountExistsDuringCallback = false
        var newAccountExistsDuringCallback = false

        val renamed = androidAccountManager.renameWithData(account, "AndroidAccountManagerTest renamed") { newAccount ->
            val accounts = accountManager.getAccountsByType(account.type)
            oldAccountExistsDuringCallback = accounts.contains(account)
            newAccountExistsDuringCallback = accounts.contains(newAccount)
        }
        currentAccount = renamed

        assertTrue("old account should still exist during onUpdate", oldAccountExistsDuringCallback)
        assertTrue("new account should exist during onUpdate", newAccountExistsDuringCallback)
    }

    @Test
    fun testRenameWithData_RollsBackWhenOnUpdateThrows() = runTest {
        val newName = "AndroidAccountManagerTest renamed"

        try {
            androidAccountManager.renameWithData(account, newName) {
                throw RuntimeException("onUpdate failed")
            }
            fail("Expected exception from onUpdate")
        } catch (_: RuntimeException) {
            // expected
        }

        // old account must still exist, new account must be gone
        val accounts = accountManager.getAccountsByType(account.type)
        assertTrue(accounts.contains(account))
        assertFalse(accounts.contains(Account(newName, account.type)))
    }

    @Test
    fun testRenameWithData_SameName_ReturnsSameAccountWithoutRenaming() = runTest {
        val result = androidAccountManager.renameWithData(account, account.name) { }

        assertSame(account, result)
        assertTrue(accountManager.getAccountsByType(account.type).contains(account))
    }

    @Test
    fun testRenameWithData_BlankName_Throws() = runTest {
        for (name in listOf("", "   ")) {
            try {
                androidAccountManager.renameWithData(account, name) { }
                fail("Expected IllegalArgumentException for name '$name'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
        assertTrue(accountManager.getAccountsByType(account.type).contains(account))
    }

    @Test
    fun testRenameWithData_NameAlreadyExists_Throws() = runTest {
        val other = TestAccount.create(accountName = "AndroidAccountManagerTest other")
        try {
            try {
                androidAccountManager.renameWithData(account, other.name) { }
                fail("Expected an exception when renaming to an existing account name")
            } catch (_: IllegalStateException) {
                // expected
            }
            // original account must still exist untouched
            assertTrue(accountManager.getAccountsByType(account.type).contains(account))
        } finally {
            TestAccount.remove(other)
        }
    }

}
