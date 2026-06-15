/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.provider.ContactsContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.mapping.contacts.Contact
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class AndroidAddressBookTest {

    companion object {
        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

        private val testAddressBookAccount = Account("AndroidAddressBookTest", "at.bitfire.vcard4android")
        private lateinit var provider: ContentProviderClient

        @BeforeClass
        @JvmStatic
        fun connect() {
            val context = InstrumentationRegistry.getInstrumentation().context
            provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
            assertNotNull(provider)
        }

        @BeforeClass
        @JvmStatic
        fun disconnect() {
            @Suppress("DEPRECATION")
            provider.release()
        }
    }


    @Test
    fun testCountContacts_empty() {
		val addressBook = TestAddressBook(testAddressBookAccount, provider)
        val count = addressBook.countContacts(null, null)
        assertEquals(0, count)
    }

    @Test
    fun testCountContacts_withContacts() {
		val addressBook = TestAddressBook(testAddressBookAccount, provider)
        
        // Create some test contacts
        val contact1 = AndroidContact(addressBook, Contact().apply { 
            displayName = "Test Contact 1"
        }, null, null)
        contact1.add()
        
        val contact2 = AndroidContact(addressBook, Contact().apply { 
            displayName = "Test Contact 2"
        }, null, null)
        contact2.add()
        
        try {
            val count = addressBook.countContacts(null, null)
            assertEquals(2, count)
        } finally {
            contact1.delete()
            contact2.delete()
        }
    }

    @Test
    fun testCountContacts_withFilter() {
		val addressBook = TestAddressBook(testAddressBookAccount, provider)
        
        // Create test contacts with different UIDs
        val contact1 = AndroidContact(addressBook, Contact().apply { 
            displayName = "Filter Test 1"
            uid = "test-uid-1"
        }, null, null)
        contact1.add()
        
        val contact2 = AndroidContact(addressBook, Contact().apply { 
            displayName = "Filter Test 2"
            uid = "test-uid-2"
        }, null, null)
        contact2.add()

        try {
            // Test counting with filter
            val filteredCount = addressBook.countContacts("${AddressContract.RawContactColumns.UID}=?", arrayOf("test-uid-1"))
            assertEquals(1, filteredCount)

            // Test counting with non-matching filter
            val noMatchCount = addressBook.countContacts("${AddressContract.RawContactColumns.UID}=?", arrayOf("non-existent"))
            assertEquals(0, noMatchCount)
        } finally {
            contact1.delete()
            contact2.delete()
        }
    }


    @Test
    fun testSettings() {
        val addressBook = TestAddressBook(testAddressBookAccount, provider)

        var values = ContentValues()
        values.put(ContactsContract.Settings.SHOULD_SYNC, false)
        values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, false)
        addressBook.settings = values
        values = addressBook.settings
        assertFalse(values.getAsInteger(ContactsContract.Settings.SHOULD_SYNC) != 0)
        assertFalse(values.getAsInteger(ContactsContract.Settings.UNGROUPED_VISIBLE) != 0)

        values = ContentValues()
        values.put(ContactsContract.Settings.SHOULD_SYNC, true)
        values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, true)
        addressBook.settings = values
        values = addressBook.settings
        assertTrue(values.getAsInteger(ContactsContract.Settings.SHOULD_SYNC) != 0)
        assertTrue(values.getAsInteger(ContactsContract.Settings.UNGROUPED_VISIBLE) != 0)
    }

    @Test
    fun testSyncState() {
		val addressBook = TestAddressBook(testAddressBookAccount, provider)

        addressBook.syncState = ByteArray(0)
        assertEquals(0, addressBook.syncState!!.size)

        val random = byteArrayOf(1, 2, 3, 4, 5)
        addressBook.syncState = random
        assertArrayEquals(random, addressBook.syncState)
    }

}
