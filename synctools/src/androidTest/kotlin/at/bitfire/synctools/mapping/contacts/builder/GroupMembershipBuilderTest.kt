/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.Manifest
import android.content.ContentProviderClient
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.TestAddressBook
import at.bitfire.synctools.vcard.GroupMethod
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class GroupMembershipBuilderTest {

    @Test
    fun testCategories_GroupsAsCategories() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val contact = Contact().apply {
                categories += "TEST GROUP"
            }
            GroupMembershipBuilder(Uri.EMPTY, null, contact, addressBook, GroupMethod.CATEGORIES, false).build().also { result ->
                assertEquals(1, result.size)
                assertEquals(GroupMembership.CONTENT_ITEM_TYPE, result[0].values[GroupMembership.MIMETYPE])
                assertEquals(addressBook.findOrCreateGroup("TEST GROUP"), result[0].values[GroupMembership.GROUP_ROW_ID])
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testCategories_GroupsAsVCards() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val contact = Contact().apply {
                categories += "TEST GROUP"
            }
            GroupMembershipBuilder(Uri.EMPTY, null, contact, addressBook, GroupMethod.GROUP_VCARDS, false).build().also { result ->
                // group membership is constructed during post-processing
                assertEquals(0, result.size)
            }
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
