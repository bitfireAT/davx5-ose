/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.Manifest
import android.content.ContentProviderClient
import android.content.ContentValues
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.AddressContract
import at.bitfire.synctools.storage.contacts.AndroidContact
import at.bitfire.synctools.storage.contacts.TestAddressBook
import at.bitfire.synctools.vcard.GroupMethod
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class GroupMembershipHandlerTest {

    @Test
    fun testMembership_GroupsAsCategories() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val groupId = addressBook.findOrCreateGroup("TEST GROUP")

            val contact = Contact()
            val androidContact = AndroidContact(addressBook, contact, null, null)
            GroupMembershipHandler(androidContact, GroupMethod.CATEGORIES).handle(ContentValues().apply {
                put(GroupMembership.GROUP_ROW_ID, groupId)
                put(AddressContract.CachedGroupMembership.RAW_CONTACT_ID, -1)
            }, contact)
            assertArrayEquals(arrayOf(groupId), androidContact.groupMemberships.toArray())
            assertArrayEquals(arrayOf("TEST GROUP"), contact.categories.toArray())
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testMembership_GroupsAsVCards() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val contact = Contact()
            val androidContact = AndroidContact(addressBook, contact, null, null)
            GroupMembershipHandler(androidContact, GroupMethod.GROUP_VCARDS).handle(ContentValues().apply {
                put(GroupMembership.GROUP_ROW_ID, 12345L)  // group doesn't have to really exist for GROUP_VCARDS
                put(AddressContract.CachedGroupMembership.RAW_CONTACT_ID, -1)
            }, contact)
            assertArrayEquals(arrayOf(12345L), androidContact.groupMemberships.toArray())
            assertTrue(contact.categories.isEmpty())
        } finally {
            TestAddressBook.remove(addressBook)
        }
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
