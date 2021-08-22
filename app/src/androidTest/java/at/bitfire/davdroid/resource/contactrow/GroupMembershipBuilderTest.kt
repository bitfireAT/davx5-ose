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

    @JvmField
    @Rule
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

    private lateinit var provider: ContentProviderClient

    private lateinit var addressBookGroupsAsCategories: LocalTestAddressBook
    private lateinit var addressBookGroupsAsVCards: LocalTestAddressBook

    @Before
    fun connect() {
        val context = InstrumentationRegistry.getInstrumentation().context
        provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
        Assert.assertNotNull(provider)

        addressBookGroupsAsCategories = LocalTestAddressBook(context, provider, GroupMethod.CATEGORIES)
        addressBookGroupsAsVCards = LocalTestAddressBook(context, provider, GroupMethod.GROUP_VCARDS)
    }

    @After
    fun disconnect() {
        @Suppress("DEPRECATION")
        provider.release()
    }


    @Test
    fun testCategories_GroupsAsCategories() {
        val contact = Contact().apply {
            categories += "TEST GROUP"
        }
        GroupMembershipBuilder(Uri.EMPTY, null, contact, addressBookGroupsAsCategories).build().also { result ->
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
        GroupMembershipBuilder(Uri.EMPTY, null, contact, addressBookGroupsAsVCards).build().also { result ->
            assertEquals(0, result.size)
        }
    }

}