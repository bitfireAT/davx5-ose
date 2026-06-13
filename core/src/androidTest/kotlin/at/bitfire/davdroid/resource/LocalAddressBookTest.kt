/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.LabeledProperty
import at.bitfire.synctools.mapping.contacts.PendingMemberships
import at.bitfire.synctools.storage.contacts.AddressContract.CachedGroupMembership
import at.bitfire.synctools.storage.contacts.AddressContract.GroupColumns
import at.bitfire.synctools.storage.contacts.AddressContract.asSyncAdapter
import at.bitfire.synctools.storage.contacts.ContactsBatchOperation
import at.bitfire.synctools.vcard.GroupMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import ezvcard.property.Telephone
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.FileNotFoundException
import java.util.LinkedList
import java.util.Optional
import javax.inject.Inject

@HiltAndroidTest
class LocalAddressBookTest {

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var localTestAddressBookProvider: LocalTestAddressBookProvider

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    val account = Account("Test Account", "Test Account Type")

    @Before
    fun setUp() {
        hiltRule.inject()
    }


    /**
     * Tests whether contacts are moved (and not lost) when an address book is renamed.
     */
    @Test
    fun test_renameAccount_retainsContacts() {
        localTestAddressBookProvider.provide(account, provider) { addressBook ->
            // insert contact with data row
            val uid = "12345"
            val contact = Contact(
                uid = uid,
                displayName = "Test Contact",
                phoneNumbers = LinkedList(listOf(LabeledProperty(Telephone("1234567890"))))
            )
            val uri = LocalContact(addressBook, contact, null, null, 0).add()
            val id = ContentUris.parseId(uri)
            val localContact = addressBook.findContactById(id)
            localContact.resetDirty()
            assertFalse("Contact is dirty before moving", isContactDirty(addressBook, id))

            // rename address book
            val newName = "New Name"
            addressBook.renameAccount(newName)
            assertEquals(newName, addressBook.addressBookAccount.name)

            // check whether contact is still here (including data rows) and not dirty
            val result = addressBook.findContactById(id)
            assertFalse("Contact is dirty after moving", isContactDirty(addressBook, id))

            val contact2 = result.getContact()
            assertEquals(uid, contact2.uid)
            assertEquals("Test Contact", contact2.displayName)
            assertEquals("1234567890", contact2.phoneNumbers.first().component1().text)
        }
    }

    /**
     * Tests whether groups are moved (and not lost) when an address book is renamed.
     */
    @Test
    fun test_renameAccount_retainsGroups() {
        localTestAddressBookProvider.provide(account, provider) { addressBook ->
            // insert group
            val localGroup = LocalGroup(addressBook, Contact(displayName = "Test Group"), null, null, 0)
            val uri = localGroup.add()
            val id = ContentUris.parseId(uri)

            // make sure it's not dirty
            localGroup.clearDirty(Optional.empty(), null, null)
            assertFalse("Group is dirty before moving", isGroupDirty(addressBook, id))

            // rename address book
            val newName = "New Name"
            assertTrue(addressBook.renameAccount(newName))
            assertEquals(newName, addressBook.addressBookAccount.name)

            // check whether group is still here and not dirty
            val result = addressBook.findGroupById(id)
            assertFalse("Group is dirty after moving", isGroupDirty(addressBook, id))

            val group = result.getContact()
            assertEquals("Test Group", group.displayName)
        }
    }


    @Test
    fun testApplyPendingMemberships_addPendingMembership() {
        localTestAddressBookProvider.provide(account, provider, GroupMethod.GROUP_VCARDS) { ab ->
            val contact1 = LocalContact(ab, Contact().apply {
                uid = "test1"
                displayName = "Test"
            }, "test1.vcf", null, 0)
            contact1.add()

            val group = newGroup(ab)
            // set pending membership of contact1
            ab.provider!!.update(
                ContentUris.withAppendedId(ab.groupsSyncUri(), group.id!!),
                contentValuesOf(GroupColumns.PENDING_MEMBERS to PendingMemberships(setOf("test1")).toString()),
                null, null
            )

            // pending membership -> contact1 should be added to group
            ab.applyPendingMemberships()

            // check group membership
            ab.provider!!.query(
                ContactsContract.Data.CONTENT_URI.asSyncAdapter(), arrayOf(GroupMembership.GROUP_ROW_ID, GroupMembership.RAW_CONTACT_ID),
                "${GroupMembership.MIMETYPE}=?", arrayOf(GroupMembership.CONTENT_ITEM_TYPE),
                null
            )!!.use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(group.id, cursor.getLong(0))
                assertEquals(contact1.id, cursor.getLong(1))

                assertFalse(cursor.moveToNext())
            }
            // check cached group membership
            ab.provider!!.query(
                ContactsContract.Data.CONTENT_URI.asSyncAdapter(), arrayOf(CachedGroupMembership.GROUP_ID, CachedGroupMembership.RAW_CONTACT_ID),
                "${CachedGroupMembership.MIMETYPE}=?", arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE),
                null
            )!!.use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(group.id, cursor.getLong(0))
                assertEquals(contact1.id, cursor.getLong(1))

                assertFalse(cursor.moveToNext())
            }
        }
    }

    @Test
    fun testApplyPendingMemberships_removeMembership() {
        localTestAddressBookProvider.provide(account, provider, GroupMethod.GROUP_VCARDS) { ab ->
            val contact1 = LocalContact(ab, Contact().apply {
                uid = "test1"
                displayName = "Test"
            }, "test1.vcf", null, 0)
            contact1.add()

            val group = newGroup(ab)

            // add contact1 to group
            val batch = ContactsBatchOperation(ab.provider!!)
            contact1.addToGroup(batch, group.id!!)
            batch.commit()

            // no pending memberships -> membership should be removed
            ab.applyPendingMemberships()

            // check group membership
            ab.provider!!.query(
                ContactsContract.Data.CONTENT_URI.asSyncAdapter(),
                arrayOf(GroupMembership.GROUP_ROW_ID, GroupMembership.RAW_CONTACT_ID),
                "${GroupMembership.MIMETYPE}=?",
                arrayOf(GroupMembership.CONTENT_ITEM_TYPE),
                null
            )!!.use { cursor ->
                assertFalse(cursor.moveToNext())
            }
            // check cached group membership
            ab.provider!!.query(
                ContactsContract.Data.CONTENT_URI.asSyncAdapter(),
                arrayOf(CachedGroupMembership.GROUP_ID, CachedGroupMembership.RAW_CONTACT_ID),
                "${CachedGroupMembership.MIMETYPE}=?",
                arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE),
                null
            )!!.use { cursor ->
                assertFalse(cursor.moveToNext())
            }
        }
    }


    // helpers

    private fun newGroup(addressBook: LocalAddressBook): LocalGroup =
        LocalGroup(addressBook, Contact().apply { displayName = "Test Group" }, null, null, 0)
            .apply { add() }

    /**
     * Returns the dirty flag of the given contact.
     *
     * @return true if the contact is dirty, false otherwise
     *
     * @throws FileNotFoundException if the contact can't be found
     */
    fun isContactDirty(adddressBook: LocalAddressBook, id: Long): Boolean {
        val uri = ContentUris.withAppendedId(adddressBook.rawContactsSyncUri(), id)
        provider.query(uri, arrayOf(ContactsContract.RawContacts.DIRTY), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst())
                return cursor.getInt(0) != 0
        }
        throw FileNotFoundException()
    }

    /**
     * Returns the dirty flag of the given contact group.
     *
     * @return true if the group is dirty, false otherwise
     *
     * @throws FileNotFoundException if the group can't be found
     */
    fun isGroupDirty(adddressBook: LocalAddressBook, id: Long): Boolean {
        val uri = ContentUris.withAppendedId(adddressBook.groupsSyncUri(), id)
        provider.query(uri, arrayOf(ContactsContract.Groups.DIRTY), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst())
                return cursor.getInt(0) != 0
        }
        throw FileNotFoundException()
    }


    companion object {

        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

        private lateinit var provider: ContentProviderClient

        @BeforeClass
        @JvmStatic
        fun connect() {
            val context = InstrumentationRegistry.getInstrumentation().context
            provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
        }

        @AfterClass
        @JvmStatic
        fun disconnect() {
            provider.close()
        }
    }

}