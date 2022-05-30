/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class LocalAddressBookTest {

    @get:Rule()
    val hiltRule = HiltAndroidRule(this)

    val context = InstrumentationRegistry.getInstrumentation().targetContext

    val mainAccountType = context.getString(R.string.account_type)
    val mainAccount = Account("main", mainAccountType)

    val addressBookAccountType = context.getString(R.string.account_type_address_book)
    val addressBookAccount = Account("sub", addressBookAccountType)

    val accountManager = AccountManager.get(context)

    @Before
    fun setUp() {
        hiltRule.inject()

        // TODO DOES NOT WORK: the account immediately starts to sync, which creates the sync adapter services.
        // The services however can't be created because Hilt is "not ready" (although it has been initialized in the line above).
        // assertTrue(AccountUtils.createAccount(context, mainAccount, AccountSettings.initialUserData(null)))
    }

    @After
    fun cleanup() {
        accountManager.removeAccount(addressBookAccount, null, null)
        accountManager.removeAccount(mainAccount, null, null)
    }


    // TODO see above
    /*@Test
    fun testMainAccount_AddressBookAccount_WithMainAccount() {
        // create address book account
        assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, Bundle().apply {
            putString(LocalAddressBook.USER_DATA_MAIN_ACCOUNT_NAME, mainAccount.name)
            putString(LocalAddressBook.USER_DATA_MAIN_ACCOUNT_TYPE, mainAccount.type)
        }))

        // check mainAccount()
        assertEquals(mainAccount, LocalAddressBook.mainAccount(context, addressBookAccount))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMainAccount_AddressBookAccount_WithoutMainAccount() {
        // create address book account
        assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, Bundle()))

        // check mainAccount(); should fail because there's no main account
        LocalAddressBook.mainAccount(context, addressBookAccount)
    }*/

    @Test(expected = IllegalArgumentException::class)
    fun testMainAccount_OtherAccount() {
        LocalAddressBook.mainAccount(context, Account("Other Account", "com.example"))
    }

}