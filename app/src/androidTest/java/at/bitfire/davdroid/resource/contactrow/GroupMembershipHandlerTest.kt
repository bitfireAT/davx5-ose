/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource.contactrow

import android.Manifest
import android.content.ContentProviderClient
import android.content.ContentValues
import android.provider.ContactsContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.resource.LocalTestAddressBook
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import org.junit.*
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue

class GroupMembershipHandlerTest {

    @JvmField
    @Rule
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

    private lateinit var provider: ContentProviderClient

    private lateinit var addressBookGroupsAsCategories: LocalTestAddressBook
    private var addressBookGroupsAsCategoriesGroup: Long = -1

    private lateinit var addressBookGroupsAsVCards: LocalTestAddressBook

    @Before
    fun connect() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
        Assert.assertNotNull(provider)

        addressBookGroupsAsCategories = LocalTestAddressBook(context, provider, GroupMethod.CATEGORIES)
        addressBookGroupsAsCategoriesGroup = addressBookGroupsAsCategories.findOrCreateGroup("TEST GROUP")

        addressBookGroupsAsVCards = LocalTestAddressBook(context, provider, GroupMethod.GROUP_VCARDS)
    }

    @After
    fun disconnect() {
        @Suppress("DEPRECATION")
        provider.release()
    }


    @Test
    fun testMembership_GroupsAsCategories() {
        val contact = Contact()
        val localContact = LocalContact(addressBookGroupsAsCategories, contact, null, null, 0)
        GroupMembershipHandler(localContact).handle(ContentValues().apply {
            put(CachedGroupMembership.GROUP_ID, addressBookGroupsAsCategoriesGroup)
            put(CachedGroupMembership.RAW_CONTACT_ID, -1)
        }, contact)
        assertArrayEquals(arrayOf(addressBookGroupsAsCategoriesGroup), localContact.groupMemberships.toArray())
        assertArrayEquals(arrayOf("TEST GROUP"), contact.categories.toArray())
    }


    @Test
    fun testMembership_GroupsAsVCards() {
        val contact = Contact()
        val localContact = LocalContact(addressBookGroupsAsVCards, contact, null, null, 0)
        GroupMembershipHandler(localContact).handle(ContentValues().apply {
            put(CachedGroupMembership.GROUP_ID, 12345)    // because the group name is not queried and put into CATEGORIES, the group doesn't have to really exist
            put(CachedGroupMembership.RAW_CONTACT_ID, -1)
        }, contact)
        assertArrayEquals(arrayOf(12345L), localContact.groupMemberships.toArray())
        assertTrue(contact.categories.isEmpty())
    }

}