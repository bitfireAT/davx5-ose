package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.provider.ContactsContract
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.SpyK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.verify
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LocalAddressBookStoreTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    val context: Context = mockk(relaxed = true) {
        every { getString(R.string.account_type_address_book) } returns "com.bitfire.davdroid.addressbook"
    }
//    val account = Account("MrRobert@example.com", "com.bitfire.davdroid.addressbook")
    val account: Account = mockk(relaxed = true) {
//        every { name } returns "MrRobert@example.com"
//        every { type } returns "com.bitfire.davdroid.addressbook"
    }
    val provider = mockk<ContentProviderClient>(relaxed = true)
    val addressBook: LocalAddressBook = mockk(relaxed = true) {
        every { updateSyncFrameworkSettings() } just runs
        every { addressBookAccount } returns account
        every { settings } returns LocalAddressBookStore.contactsProviderSettings
    }

    @SpyK
    @InjectMockKs
    var localAddressBookStore = LocalAddressBookStore(
        collectionRepository = mockk(relaxed = true),
        context = context,
        localAddressBookFactory = mockk(relaxed = true) {
            every { create(account, provider) } returns addressBook
        },
        logger = mockk(relaxed = true),
        serviceRepository = mockk(relaxed = true) {
            every { get(any<Long>()) } returns null
            every { get(200) } returns mockk<Service> {
                every { accountName } returns "MrRobert@example.com"
            }
        },
        settings = mockk(relaxed = true)
    )


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
        val collection = mockk<Collection>(relaxed = true)  {
            every { id } returns 1
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
        }
        every { localAddressBookStore.createAccount(any(), any(), any()) } returns null
        assertEquals(null, localAddressBookStore.create(provider, collection))
    }

    @Test
    fun test_create_createAccountReturnsAccount() {
        val collection = mockk<Collection>(relaxed = true)  {
            every { id } returns 1
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
        }
        every { localAddressBookStore.createAccount(any(), any(), any()) } returns account
        every { addressBook.readOnly } returns true
        val addrBook = localAddressBookStore.create(provider, collection)!!

        verify(exactly = 1) { addressBook.updateSyncFrameworkSettings() }
        assertEquals(account, addrBook.addressBookAccount)
        assertEquals(LocalAddressBookStore.contactsProviderSettings, addrBook.settings)
        assertEquals(true, addrBook.readOnly)

        every { addressBook.readOnly } returns false
        val addrBook2 = localAddressBookStore.create(provider, collection)!!
        assertEquals(false, addrBook2.readOnly)
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