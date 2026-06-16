/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.Manifest
import android.content.ContentProviderClient
import android.content.ContentValues
import android.provider.ContactsContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.AddressContract
import at.bitfire.synctools.storage.contacts.AndroidContact
import at.bitfire.synctools.storage.contacts.TestAddressBook
import at.bitfire.synctools.vcard.GroupMethod
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class CachedGroupMembershipHandlerTest {

    @Test
    fun testMembership() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val contact = Contact()
            val androidContact = AndroidContact(addressBook, contact, null, null)
            CachedGroupMembershipHandler(androidContact, GroupMethod.GROUP_VCARDS).handle(ContentValues().apply {
                put(AddressContract.CachedGroupMembership.GROUP_ID, 123456)
                put(AddressContract.CachedGroupMembership.RAW_CONTACT_ID, 789)
            }, contact)
            assertArrayEquals(arrayOf(123456L), androidContact.cachedGroupMemberships.toArray())
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
