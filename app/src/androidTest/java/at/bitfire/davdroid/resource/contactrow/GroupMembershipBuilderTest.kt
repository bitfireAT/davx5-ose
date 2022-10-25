/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource.contactrow

import android.Manifest
import android.content.ContentProviderClient
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.resource.LocalTestAddressBook
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import org.junit.*
import org.junit.Assert.assertEquals

class GroupMembershipBuilderTest {

    companion object {
        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

        private lateinit var provider: ContentProviderClient

        private lateinit var addressBookGroupsAsCategories: LocalTestAddressBook
        private lateinit var addressBookGroupsAsVCards: LocalTestAddressBook

        @BeforeClass
        @JvmStatic
        fun connect() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
            Assert.assertNotNull(provider)

            addressBookGroupsAsCategories = LocalTestAddressBook(context, provider, GroupMethod.CATEGORIES)
            addressBookGroupsAsVCards = LocalTestAddressBook(context, provider, GroupMethod.GROUP_VCARDS)
        }

        @AfterClass
        @JvmStatic
        fun disconnect() {
            @Suppress("DEPRECATION")
            provider.release()
        }
    }


    @Test
    fun testCategories_GroupsAsCategories() {
        val contact = Contact().apply {
            categories += "TEST GROUP"
        }
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
        GroupMembershipBuilder(Uri.EMPTY, null, contact, addressBookGroupsAsVCards, false).build().also { result ->
            // group membership is constructed during post-processing
            assertEquals(0, result.size)
        }
    }

}