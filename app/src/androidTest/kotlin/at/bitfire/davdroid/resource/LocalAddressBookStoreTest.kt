/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.account.SystemAccountUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class LocalAddressBookStoreTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    @SpyK
    lateinit var context: Context

    val account by lazy { Account("Test Account", context.getString(R.string.account_type)) }
    val addressBookAccount by lazy { Account("MrRobert@example.com", context.getString(R.string.account_type_address_book)) }

    val provider = mockk<ContentProviderClient>(relaxed = true)
    val addressBook: LocalAddressBook = mockk(relaxed = true) {
        every { account } answers { this@LocalAddressBookStoreTest.account }
        every { updateSyncFrameworkSettings() } just runs
        every { addressBookAccount } answers { this@LocalAddressBookStoreTest.addressBookAccount }
        every { settings } returns LocalAddressBookStore.contactsProviderSettings
    }

    @Suppress("unused")     // used by @InjectMockKs LocalAddressBookStore
    @RelaxedMockK
    lateinit var collectionRepository: DavCollectionRepository

    @Suppress("unused")     // used by @InjectMockKs LocalAddressBookStore
    val localAddressBookFactory = mockk<LocalAddressBook.Factory> {
        every { create(any(), any(), provider) } returns addressBook
    }

    @Inject
    @SpyK
    lateinit var logger: Logger

    @RelaxedMockK
    lateinit var settingsManager: SettingsManager

    @Suppress("unused")     // used by @InjectMockKs LocalAddressBookStore
    val serviceRepository = mockk<DavServiceRepository>(relaxed = true) {
        every { get(any<Long>()) } returns null
        every { get(200) } returns mockk<Service> {
            every { accountName } returns "MrRobert@example.com"
        }
    }

    @InjectMockKs
    @SpyK
    lateinit var localAddressBookStore: LocalAddressBookStore


    @Before
    fun setUp() {
        hiltRule.inject()

        // initialize global mocks
        MockKAnnotations.init(this)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }


    @Test
    fun test_accountName_missingService() {
        val collection = mockk<Collection> {
            every { id } returns 42
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
            every { displayName } returns null
            every { serviceId } returns 404
        }
        assertEquals("funnyfriends #42", localAddressBookStore.accountName(collection))
    }

    @Test
    fun test_accountName_missingDisplayName() {
        val collection = mockk<Collection> {
            every { id } returns 42
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
            every { displayName } returns null
            every { serviceId } returns 200
        }
        val accountName = localAddressBookStore.accountName(collection)
        assertEquals("funnyfriends (MrRobert@example.com) #42", accountName)
    }

    @Test
    fun test_accountName_missingDisplayNameAndService() {
        val collection = mockk<Collection>(relaxed = true) {
            every { id } returns 1
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
            every { displayName } returns null
            every { serviceId } returns 404 // missing service
        }
        assertEquals("funnyfriends #1", localAddressBookStore.accountName(collection))
    }


    @Test
    fun test_create_createAccountReturnsNull() {
        val collection = mockk<Collection>(relaxed = true) {
            every { serviceId } returns 200
            every { id } returns 1
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
        }
        every { localAddressBookStore.createAddressBookAccount(any(), any(), any(), any()) } returns null
        assertEquals(null, localAddressBookStore.create(provider, collection))
    }

    @Test
    fun test_create_createAccountReturnsAccount() {
        val collection = mockk<Collection>(relaxed = true) {
            every { serviceId } returns 200
            every { id } returns 1
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
        }
        every { localAddressBookStore.createAddressBookAccount(any(), any(), any(), any()) } returns addressBookAccount
        every { addressBook.readOnly } returns true
        val addrBook = localAddressBookStore.create(provider, collection)!!

        verify(exactly = 1) { addressBook.updateSyncFrameworkSettings() }
        assertEquals(addressBookAccount, addrBook.addressBookAccount)
        assertEquals(LocalAddressBookStore.contactsProviderSettings, addrBook.settings)
        assertEquals(true, addrBook.readOnly)

        every { addressBook.readOnly } returns false
        val addrBook2 = localAddressBookStore.create(provider, collection)!!
        assertEquals(false, addrBook2.readOnly)
    }

    @Test
    fun test_createAccount_succeeds() {
        mockkObject(SystemAccountUtils)
        every { SystemAccountUtils.createAccount(any(), any(), any()) } returns true
        val result: Account = localAddressBookStore.createAddressBookAccount(
            account, "MrRobert@example.com", 42, "https://example.com/addressbook/funnyfriends"
        )!!
        verify(exactly = 1) { SystemAccountUtils.createAccount(any(), any(), any()) }
        assertEquals("MrRobert@example.com", result.name)
        assertEquals(context.getString(R.string.account_type_address_book), result.type)
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

}