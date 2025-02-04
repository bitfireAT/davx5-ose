/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.contactrow

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.resource.LocalTestAddressBook
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class GroupMembershipBuilderTest {

    @Inject @ApplicationContext
    lateinit var context: Context

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    val account = Account("Test Account", "Test Account Type")
    private lateinit var provider: ContentProviderClient

    @Before
    fun setUp() {
        ContentResolver.setMasterSyncAutomatically(false)
        hiltRule.inject()

        provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
    }

    @After
    fun tearDown() {
        provider.close()
    }


    @Test
    fun testCategories_GroupsAsCategories() {
        val contact = Contact().apply {
            categories += "TEST GROUP"
        }
        val addressBookGroupsAsCategories = LocalTestAddressBook.create(context, account, provider, GroupMethod.CATEGORIES)
        try {
            GroupMembershipBuilder(Uri.EMPTY, null, contact, addressBookGroupsAsCategories, false).build().also { result ->
                assertEquals(1, result.size)
                assertEquals(GroupMembership.CONTENT_ITEM_TYPE, result[0].values[GroupMembership.MIMETYPE])
                assertEquals(addressBookGroupsAsCategories.findOrCreateGroup("TEST GROUP"), result[0].values[GroupMembership.GROUP_ROW_ID])
            }
        } finally {
            addressBookGroupsAsCategories.remove()
        }
    }

    @Test
    fun testCategories_GroupsAsVCards() {
        val contact = Contact().apply {
            categories += "TEST GROUP"
        }
        val addressBookGroupsAsVCards = LocalTestAddressBook.create(context, account, provider, GroupMethod.GROUP_VCARDS)
        try {
            GroupMembershipBuilder(Uri.EMPTY, null, contact, addressBookGroupsAsVCards, false).build().also { result ->
                // group membership is constructed during post-processing
                assertEquals(0, result.size)
            }
        } finally {
            addressBookGroupsAsVCards.remove()
        }
    }

}