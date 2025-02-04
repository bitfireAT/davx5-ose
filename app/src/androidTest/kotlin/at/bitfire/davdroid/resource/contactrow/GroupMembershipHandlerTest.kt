/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.contactrow

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.resource.LocalTestAddressBook
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class GroupMembershipHandlerTest {

    @Inject @ApplicationContext
    lateinit var context: Context

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    val account = Account("Test Account", "Test Account Type")
    private lateinit var provider: ContentProviderClient

    @Before
    fun setUp() {
        hiltRule.inject()

        provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
    }

    @After
    fun tearDown() {
        provider.close()
    }


    @Test
    fun testMembership_GroupsAsCategories() {
        val addressBookGroupsAsCategories = LocalTestAddressBook.create(context, account, provider, GroupMethod.CATEGORIES)
        try {
            val addressBookGroupsAsCategoriesGroup = addressBookGroupsAsCategories.findOrCreateGroup("TEST GROUP")

            val contact = Contact()
            val localContact = LocalContact(addressBookGroupsAsCategories, contact, null, null, 0)
            GroupMembershipHandler(localContact).handle(ContentValues().apply {
                put(CachedGroupMembership.GROUP_ID, addressBookGroupsAsCategoriesGroup)
                put(CachedGroupMembership.RAW_CONTACT_ID, -1)
            }, contact)
            assertArrayEquals(arrayOf(addressBookGroupsAsCategoriesGroup), localContact.groupMemberships.toArray())
            assertArrayEquals(arrayOf("TEST GROUP"), contact.categories.toArray())
        } finally {
            addressBookGroupsAsCategories.remove()
        }
    }


    @Test
    fun testMembership_GroupsAsVCards() {
        val addressBookGroupsAsVCards = LocalTestAddressBook.create(context, account, provider, GroupMethod.GROUP_VCARDS)
        try {
            val contact = Contact()
            val localContact = LocalContact(addressBookGroupsAsVCards, contact, null, null, 0)
            GroupMembershipHandler(localContact).handle(ContentValues().apply {
                put(CachedGroupMembership.GROUP_ID, 12345)    // because the group name is not queried and put into CATEGORIES, the group doesn't have to really exist
                put(CachedGroupMembership.RAW_CONTACT_ID, -1)
            }, contact)
            assertArrayEquals(arrayOf(12345L), localContact.groupMemberships.toArray())
            assertTrue(contact.categories.isEmpty())
        } finally {
            addressBookGroupsAsVCards.remove()
        }
    }

}