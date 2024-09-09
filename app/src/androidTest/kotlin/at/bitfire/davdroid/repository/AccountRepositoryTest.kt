/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalAddressBook.Companion.USER_DATA_COLLECTION_ID
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.TasksAppManager
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class AccountRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    private val addressBookAccountType by lazy { context.getString(R.string.account_type_address_book) }
    private val addressBookAccount by lazy { Account("sub", addressBookAccountType) }

    private val accountManager by lazy { AccountManager.get(context) }

    private val collectionRepository = mockk<DavCollectionRepository>(relaxed = true)
    private val serviceRepository = mockk<DavServiceRepository>(relaxed = true)
    private val accountRepository by lazy { AccountRepository(
        mockk<AccountSettings.Factory>(relaxed = true),
        context,
        collectionRepository,
        mockk<DavHomeSetRepository>(relaxed = true),
        mockk<Logger>(relaxed = true),
        mockk<SettingsManager>(relaxed = true),
        serviceRepository,
        mockk<Lazy<TasksAppManager>>(relaxed = true),
    ) }

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        accountManager.removeAccountExplicitly(addressBookAccount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMainAccount_OtherAccount() {
        accountRepository.mainAccount(Account("Other Account", "com.example"))
    }

    @Test
    fun testMainAccount_AddressBookAccount_WithMainAccount() {
        // create address book account
        assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, Bundle(1).apply {
            putString(USER_DATA_COLLECTION_ID, "0")
        }))
        assertEquals("0", accountManager.getUserData(addressBookAccount, USER_DATA_COLLECTION_ID))

        // mock main account
        val mainAccount = Account("Main Account", context.getString(R.string.account_type))

        // mock repository calls
        every { collectionRepository.get(0L) } returns mockk<Collection>(relaxed = true) {
            every { id } returns 0L
            every { serviceId } returns 0L
        }
        every { serviceRepository.get(0L) } returns mockk<Service>(relaxed = true) {
            every { accountName } returns mainAccount.name
        }

        // check mainAccount(); should find main account of address book account
        assertEquals(mainAccount, accountRepository.mainAccount(addressBookAccount))
    }

    @Test
    fun testMainAccount_AddressBookAccount_WithoutMainAccount() {
        // create address book account
        assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, Bundle.EMPTY))

        // check mainAccount(); should fail because there's no main account
        assertNull(accountRepository.mainAccount(addressBookAccount))
    }

}