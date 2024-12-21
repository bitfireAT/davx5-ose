/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.contactrow

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.resource.LocalTestAddressBook
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class GroupMembershipBuilderTest {

    companion object {

        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

        private lateinit var provider: ContentProviderClient

        @BeforeClass
        @JvmStatic
        fun connect() {
            val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
            provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
        }

        @AfterClass
        @JvmStatic
        fun disconnect() {
            provider.close()
        }

    }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var addressbookFactory: LocalTestAddressBook.Factory

    @Inject
    @ApplicationContext
    lateinit var context: Context

    val account = Account("Test Account", "Test Account Type")

    @Before
    fun inject() {
        hiltRule.inject()
    }


    @Test
    fun testCategories_GroupsAsCategories() {
        val contact = Contact().apply {
            categories += "TEST GROUP"
        }
        val addressBookGroupsAsCategories = addressbookFactory.create(account, provider, GroupMethod.CATEGORIES)
        GroupMembershipBuilder(Uri.EMPTY, null, contact, addressBookGroupsAsCategories, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals(GroupMembership.CONTENT_ITEM_TYPE, result[0].values[GroupMembership.MIMETYPE])
            assertEquals(addressBookGroupsAsCategories.findOrCreateGroup("TEST GROUP"), result[0].values[GroupMembership.GROUP_ROW_ID])
        }
    }

    @Test
    fun testCategories_GroupsAsVCards() {
        val contact = Contact().apply {
            categories += "TEST GROUP"
        }
        val addressBookGroupsAsVCards = addressbookFactory.create(account, provider, GroupMethod.GROUP_VCARDS)
        GroupMembershipBuilder(Uri.EMPTY, null, contact, addressBookGroupsAsVCards, false).build().also { result ->
            // group membership is constructed during post-processing
            assertEquals(0, result.size)
        }
    }

}