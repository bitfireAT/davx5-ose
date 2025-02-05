/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class LocalAddressBookStoreTest {

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var localAddressBookStore: LocalAddressBookStore

    @RelaxedMockK
    lateinit var provider: ContentProviderClient

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    lateinit var addressBookAccountType: String

    lateinit var addressBookAccount: Account
    lateinit var account: Account
    lateinit var service: Service

    @Before
    fun setUp() {
        hiltRule.inject()

        addressBookAccountType = context.getString(R.string.account_type_address_book)
        removeAddressBooks()    // FIXME we shouldn't rely on that

        account = TestAccount.create()
        service = Service(
            id = 200,
            accountName = account.name,
            type = Service.Companion.TYPE_CARDDAV,
            principal = null
        )
        db.serviceDao().insertOrReplace(service)
        addressBookAccount = Account(
            "MrRobert@example.com",
            addressBookAccountType
        )
    }

    @After
    fun tearDown() {
        removeAddressBooks()        // FIXME remove correct address book / use provide()
        TestAccount.remove(account)
    }


    @Test
    fun test_accountName_missingService() {
        val collection = mockk<Collection> {
            every { id } returns 42
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
            every { displayName } returns null
            every { serviceId } returns 404     // missing service
        }
        assertEquals("funnyfriends #42", localAddressBookStore.accountName(collection))
    }

    @Test
    fun test_accountName_missingDisplayName() {
        val collection = mockk<Collection> {
            every { id } returns 42
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
            every { displayName } returns null
            every { serviceId } returns service.id
        }
        val accountName = localAddressBookStore.accountName(collection)
        assertEquals("funnyfriends (${account.name}) #42", accountName)
    }

    @Test
    fun test_accountName_missingDisplayNameAndService() {
        val collection = mockk<Collection> {
            every { id } returns 1
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
            every { displayName } returns null
            every { serviceId } returns 404     // missing service
        }
        assertEquals("funnyfriends #1", localAddressBookStore.accountName(collection))
    }


    @Test
    fun test_create_createAccountReturnsNull() {
        val collection = mockk<Collection>(relaxed = true) {
            every { serviceId } returns service.id
            every { id } returns 1
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
        }

        mockkObject(localAddressBookStore)
        every { localAddressBookStore.createAddressBookAccount(any(), any(), any()) } returns null

        assertEquals(null, localAddressBookStore.create(provider, collection))
    }

    @Test
    fun test_create_ReadOnly() {
        val collection = mockk<Collection>(relaxed = true) {
            every { serviceId } returns service.id
            every { id } returns 1
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
            every { readOnly() } returns true
        }
        val addrBook = localAddressBookStore.create(provider, collection)!!
        assertEquals(Account("funnyfriends (Test Account) #1", addressBookAccountType), addrBook.addressBookAccount)
        assertTrue(addrBook.readOnly)
    }

    @Test
    fun test_create_ReadWrite() {
        val collection = mockk<Collection>(relaxed = true) {
            every { serviceId } returns service.id
            every { id } returns 1
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
            every { readOnly() } returns false
        }

        val addrBook = localAddressBookStore.create(provider, collection)!!
        assertEquals(Account("funnyfriends (Test Account) #1", addressBookAccountType), addrBook.addressBookAccount)
        assertFalse(addrBook.readOnly)
    }


    @Test
    fun test_getAll_differentAccount() {
        val accountManager = AccountManager.get(context)
        mockkObject(accountManager)
        every { accountManager.getAccountsByType(any()) } returns arrayOf(addressBookAccount)
        every { accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_NAME) } returns "Another Unrelated Account"
        every { accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_TYPE) } returns account.type
        val result = localAddressBookStore.getAll(account, provider)
        assertTrue(result.isEmpty())
    }

    @Test
    fun test_getAll_sameAccount() {
        val accountManager = AccountManager.get(context)
        mockkObject(accountManager)
        every { accountManager.getAccountsByType(any()) } returns arrayOf(addressBookAccount)
        every { accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_NAME) } returns account.name
        every { accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_TYPE) } returns account.type
        val result = localAddressBookStore.getAll(account, provider)
        assertEquals(1, result.size)
        assertEquals(addressBookAccount, result.first().addressBookAccount)
    }


    /**
     * Tests the calculation of read only state is correct
     */
    @Test
    fun test_shouldBeReadOnly() {
        val collectionReadOnly = mockk<Collection> { every { readOnly() } returns true }
        assertTrue(LocalAddressBookStore.shouldBeReadOnly(collectionReadOnly, false))
        assertTrue(LocalAddressBookStore.shouldBeReadOnly(collectionReadOnly, true))

        val collectionNotReadOnly = mockk<Collection> { every { readOnly() } returns false }
        assertFalse(LocalAddressBookStore.shouldBeReadOnly(collectionNotReadOnly, false))
        assertTrue(LocalAddressBookStore.shouldBeReadOnly(collectionNotReadOnly, true))
    }


    // helpers

    private fun removeAddressBooks() {
        val accountManager = AccountManager.get(context)
        accountManager.getAccountsByType(addressBookAccountType).forEach {
            accountManager.removeAccount(it, null, null)
        }
    }

}