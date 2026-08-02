/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.Manifest
import android.content.ContentProviderClient
import android.provider.ContactsContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.mapping.contacts.Contact
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class AndroidGroupTest {

    companion object {
        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

        private lateinit var provider: ContentProviderClient
        private lateinit var addressBook: AndroidAddressBook

        @BeforeClass
        @JvmStatic
        fun connect() {
            val context = InstrumentationRegistry.getInstrumentation().context
            provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
            assertNotNull(provider)

            addressBook = TestAddressBook.create(provider)
        }

        @AfterClass
        @JvmStatic
        fun disconnect() {
            TestAddressBook.remove(addressBook)
            @Suppress("DEPRECATION")
            provider.release()
        }
    }

    @Before
    fun setUp() {
        removeGroups()
    }

    @After
    fun tearDown() {
        removeGroups()
    }

    private fun removeGroups() = runBlocking {
        addressBook.provider.delete(addressBook.groupsSyncUri(), null, null)
        assertEquals(0, addressBook.countGroups(null, null))
    }


    @Test
    fun testCreateReadDeleteGroup() = runTest {
        val contact = Contact()
        contact.displayName = "at.bitfire.vcard4android-AndroidGroupTest"
        contact.note = "(test group)"

        // ensure we start without this group
        assertEquals(0, addressBook.countGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)))

        // create group
        val group = AndroidGroup(addressBook, contact, null, null)
        group.add()
        val groups =
            addressBook.queryGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)).toList()
        assertEquals(1, groups.size)
        val contact2 = groups.first().getContact()
        assertEquals(contact.displayName, contact2.displayName)
        assertEquals(contact.note, contact2.note)

        // delete group
        group.delete()
        assertEquals(0, addressBook.countGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)))
    }

    @Test
    fun testAdd_readOnly() = runTest {
        addressBook.readOnly = true

        val contact = Contact()
        contact.displayName = "at.bitfire.vcard4android-AndroidGroupTest"
        contact.note = "(test group)"

        // ensure we start without this group
        assertEquals(0, addressBook.countGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)))

        // create group
        val group = AndroidGroup(addressBook, contact, null, null)
        group.add()
        assertEquals(1, addressBook.countGroups("${ContactsContract.Groups.GROUP_IS_READ_ONLY}=?", arrayOf("1")))

        // delete group
        group.delete()
        assertEquals(0, addressBook.countGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)))
    }

    @Test
    fun testAdd_notReadOnly() = runTest {
        addressBook.readOnly = false

        val contact = Contact()
        contact.displayName = "at.bitfire.vcard4android-AndroidGroupTest"
        contact.note = "(test group)"

        // ensure we start without this group
        assertEquals(0, addressBook.countGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)))

        // create group
        val group = AndroidGroup(addressBook, contact, null, null)
        group.add()
        assertEquals(1, addressBook.countGroups("${ContactsContract.Groups.GROUP_IS_READ_ONLY}=?", arrayOf("0")))

        // delete group
        group.delete()
        assertEquals(0, addressBook.countGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)))
    }

    @Test
    fun testGetMembers_empty() {
        val group = AndroidGroup(addressBook, Contact().apply { displayName = "Test Group" })
        group.add()

        assertTrue(group.getMembers().isEmpty())
    }

    @Test
    fun testGetMembers() {
        val group = AndroidGroup(addressBook, Contact().apply { displayName = "Test Group" })
        group.add()

        val contact = AndroidContact(addressBook, Contact().apply { displayName = "Test Contact" }, null, null, 0)
        contact.add()

        val batch = ContactsBatchOperation(addressBook.provider)
        contact.addToGroup(batch, group.id!!)
        batch.commit()

        val members = group.getMembers()
        assertEquals(1, members.size)
        assertEquals(contact.id, members.first())
    }

}
