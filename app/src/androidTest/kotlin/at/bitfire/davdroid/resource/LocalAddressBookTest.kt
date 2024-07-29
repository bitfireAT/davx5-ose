/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class LocalAddressBookTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    lateinit var mainAccount: Account

    private val addressBookAccountType by lazy { context.getString(R.string.account_type_address_book) }
    private val addressBookAccount by lazy { Account("sub", addressBookAccountType) }

    private val accountManager by lazy { AccountManager.get(context) }

    @Before
    fun setUp() {
        hiltRule.inject()

        mainAccount = TestAccountAuthenticator.create()
    }

    @After
    fun tearDown() {
        accountManager.removeAccountExplicitly(addressBookAccount)
        TestAccountAuthenticator.remove(mainAccount)
    }


    @Test
    fun testMainAccount_AddressBookAccount_WithMainAccount() {
        // create address book account
        assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, Bundle(2).apply {
            putString(LocalAddressBook.USER_DATA_MAIN_ACCOUNT_NAME, mainAccount.name)
            putString(LocalAddressBook.USER_DATA_MAIN_ACCOUNT_TYPE, mainAccount.type)
        }))

        // check mainAccount()
        assertEquals(mainAccount, LocalAddressBook.mainAccount(context, addressBookAccount))
    }

    fun testMainAccount_AddressBookAccount_WithoutMainAccount() {
        // create address book account
        assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, Bundle.EMPTY))

        // check mainAccount(); should fail because there's no main account
        assertNull(LocalAddressBook.mainAccount(context, addressBookAccount))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMainAccount_OtherAccount() {
        LocalAddressBook.mainAccount(context, Account("Other Account", "com.example"))
    }

}