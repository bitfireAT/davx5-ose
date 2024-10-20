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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import at.bitfire.vcard4android.LabeledProperty
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import ezvcard.property.Telephone
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.LinkedList
import javax.inject.Inject

@HiltAndroidTest
class LocalAddressBookTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var addressbookFactory: LocalTestAddressBook.Factory

    @Inject
    @ApplicationContext
    lateinit var context: Context


    @Before
    fun setup() {
        hiltRule.inject()
    }


    /**
     * Tests whether contacts are moved (and not lost) when an address book is renamed.
     */
    @Test
    fun test_renameAccount_retainsContacts() {
        val addressBook = addressbookFactory.create(provider, GroupMethod.CATEGORIES)
        LocalTestAddressBook.createAccount(context)
        try {
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
            assertFalse("Contact is dirty before moving", addressBook.isContactDirty(id))

            // rename address book
            val newName = "New Name"
            addressBook.renameAccount(newName)
            assertEquals(Account(newName, LocalTestAddressBook.ACCOUNT.type), addressBook.account)

            // check whether contact is still here (including data rows) and not dirty
            val result = addressBook.findContactById(id)
            assertFalse("Contact is dirty after moving", addressBook.isContactDirty(id))

            val contact2 = result.getContact()
            assertEquals(uid, contact2.uid)
            assertEquals(contact.displayName, contact2.displayName)
            assertEquals(contact.phoneNumbers.first().component1().text, contact2.phoneNumbers.first().component1().text)

        } finally {
            // clean up / remove address book
            addressBook.deleteCollection()
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
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
            assertNotNull(provider)
        }

        @AfterClass
        @JvmStatic
        fun disconnect() {
            provider.close()
        }
    }

}