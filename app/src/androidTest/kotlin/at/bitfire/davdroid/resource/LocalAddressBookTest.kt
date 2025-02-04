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
import androidx.test.rule.GrantPermissionRule
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.LabeledProperty
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import ezvcard.property.Telephone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.LinkedList
import javax.inject.Inject

@HiltAndroidTest
class LocalAddressBookTest {

    @Inject @ApplicationContext
    lateinit var context: Context

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    private lateinit var provider: ContentProviderClient

    val account = Account("Test Account", "Test Account Type")
    lateinit var addressBook: LocalTestAddressBook


    @Before
    fun setUp() {
        hiltRule.inject()

        provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
        addressBook = LocalTestAddressBook.create(context, account, provider)
    }

    @After
    fun tearDown() {
        addressBook.remove()
        provider.close()
    }


    /**
     * Tests whether contacts are moved (and not lost) when an address book is renamed.
     */
    @Test
    fun test_renameAccount_retainsContacts() {
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
        assertEquals(newName, addressBook.addressBookAccount.name)

        // check whether contact is still here (including data rows) and not dirty
        val result = addressBook.findContactById(id)
        assertFalse("Contact is dirty after moving", addressBook.isContactDirty(id))

        val contact2 = result.getContact()
        assertEquals(uid, contact2.uid)
        assertEquals("Test Contact", contact2.displayName)
        assertEquals("1234567890", contact2.phoneNumbers.first().component1().text)
    }

    /**
     * Tests whether groups are moved (and not lost) when an address book is renamed.
     */
    @Test
    fun test_renameAccount_retainsGroups() {
        // insert group
        val localGroup = LocalGroup(addressBook, Contact(displayName = "Test Group"), null, null, 0)
        val uri = localGroup.add()
        val id = ContentUris.parseId(uri)

        // make sure it's not dirty
        localGroup.clearDirty(null, null, null)
        assertFalse("Group is dirty before moving", addressBook.isGroupDirty(id))

        // rename address book
        val newName = "New Name"
        assertTrue(addressBook.renameAccount(newName))
        assertEquals(newName, addressBook.addressBookAccount.name)

        // check whether group is still here and not dirty
        val result = addressBook.findGroupById(id)
        assertFalse("Group is dirty after moving", addressBook.isGroupDirty(id))

        val group = result.getContact()
        assertEquals("Test Group", group.displayName)
    }

}