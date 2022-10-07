/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.Manifest
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.*
import org.junit.Assert.*
import javax.inject.Inject

@HiltAndroidTest
class LocalGroupTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settingsManager: SettingsManager

    @Before
    fun inject() {
        hiltRule.inject()
    }

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
            assertNotNull(provider)

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

    private fun newGroup(addressBook: LocalAddressBook = addressBookGroupsAsCategories): LocalGroup =
        LocalGroup(addressBook,
            Contact().apply {
                displayName = "Test Group"
            }, null, null, 0
        ).apply {
            add()
        }



    @Before
    fun clearContacts() {
        addressBookGroupsAsCategories.clear()
        addressBookGroupsAsVCards.clear()
    }


    @Test
    fun testApplyPendingMemberships_addPendingMembership() {
        val ab = addressBookGroupsAsVCards

        val contact1 = LocalContact(ab, Contact().apply {
            uid = "test1"
            displayName = "Test"
        }, "test1.vcf", null, 0)
        contact1.add()

        val group = newGroup(ab)
        // set pending membership of contact1
        ab.provider!!.update(
            ContentUris.withAppendedId(ab.groupsSyncUri(), group.id!!),
            ContentValues().apply {
                put(LocalGroup.COLUMN_PENDING_MEMBERS, LocalGroup.PendingMemberships(setOf("test1")).toString())
            },
            null, null
        )

        // pending membership -> contact1 should be added to group
        LocalGroup.applyPendingMemberships(ab)

        // check group membership
        ab.provider!!.query(
            ab.syncAdapterURI(ContactsContract.Data.CONTENT_URI), arrayOf(GroupMembership.GROUP_ROW_ID, GroupMembership.RAW_CONTACT_ID),
            "${GroupMembership.MIMETYPE}=?", arrayOf(GroupMembership.CONTENT_ITEM_TYPE),
            null
        )!!.use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(group.id, cursor.getLong(0))
            assertEquals(contact1.id, cursor.getLong(1))

            assertFalse(cursor.moveToNext())
        }
        // check cached group membership
        ab.provider!!.query(
            ab.syncAdapterURI(ContactsContract.Data.CONTENT_URI), arrayOf(CachedGroupMembership.GROUP_ID, CachedGroupMembership.RAW_CONTACT_ID),
            "${CachedGroupMembership.MIMETYPE}=?", arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE),
            null
        )!!.use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(group.id, cursor.getLong(0))
            assertEquals(contact1.id, cursor.getLong(1))

            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun testApplyPendingMemberships_removeMembership() {
        val ab = addressBookGroupsAsVCards

        val contact1 = LocalContact(ab, Contact().apply {
            uid = "test1"
            displayName = "Test"
        }, "test1.vcf", null, 0)
        contact1.add()

        val group = newGroup(ab)

        // add contact1 to group
        val batch = BatchOperation(ab.provider!!)
        contact1.addToGroup(batch, group.id!!)
        batch.commit()

        // no pending memberships -> membership should be removed
        LocalGroup.applyPendingMemberships(ab)

        // check group membership
        ab.provider!!.query(
            ab.syncAdapterURI(ContactsContract.Data.CONTENT_URI), arrayOf(GroupMembership.GROUP_ROW_ID, GroupMembership.RAW_CONTACT_ID),
            "${GroupMembership.MIMETYPE}=?", arrayOf(GroupMembership.CONTENT_ITEM_TYPE),
            null
        )!!.use { cursor ->
            assertFalse(cursor.moveToNext())
        }
        // check cached group membership
        ab.provider!!.query(
            ab.syncAdapterURI(ContactsContract.Data.CONTENT_URI), arrayOf(CachedGroupMembership.GROUP_ID, CachedGroupMembership.RAW_CONTACT_ID),
            "${CachedGroupMembership.MIMETYPE}=?", arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE),
            null
        )!!.use { cursor ->
            assertFalse(cursor.moveToNext())
        }
    }


    @Test
    fun testClearDirty_addCachedGroupMembership() {
        val ab = addressBookGroupsAsCategories
        val group = newGroup(ab)

        val contact1 = LocalContact(ab, Contact().apply { displayName = "Test" }, "fn.vcf", null, 0)
        contact1.add()

        // insert group membership, but no cached group membership
        ab.provider!!.insert(
            ab.syncAdapterURI(ContactsContract.Data.CONTENT_URI), ContentValues().apply {
                put(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                put(GroupMembership.RAW_CONTACT_ID, contact1.id)
                put(GroupMembership.GROUP_ROW_ID, group.id)
            }
        )

        group.clearDirty(null, null)

        // check cached group membership
        ab.provider!!.query(
            ab.syncAdapterURI(ContactsContract.Data.CONTENT_URI), arrayOf(CachedGroupMembership.GROUP_ID, CachedGroupMembership.RAW_CONTACT_ID),
            "${CachedGroupMembership.MIMETYPE}=?", arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE),
            null
        )!!.use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(group.id, cursor.getLong(0))
            assertEquals(contact1.id, cursor.getLong(1))

            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun testClearDirty_removeCachedGroupMembership() {
        val ab = addressBookGroupsAsCategories
        val group = newGroup(ab)

        val contact1 = LocalContact(ab, Contact().apply { displayName = "Test" }, "fn.vcf", null, 0)
        contact1.add()

        // insert cached group membership, but no group membership
        ab.provider!!.insert(
            ab.syncAdapterURI(ContactsContract.Data.CONTENT_URI), ContentValues().apply {
                put(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                put(CachedGroupMembership.RAW_CONTACT_ID, contact1.id)
                put(CachedGroupMembership.GROUP_ID, group.id)
            }
        )

        group.clearDirty(null, null)

        // cached group membership should be gone
        ab.provider!!.query(
            ab.syncAdapterURI(ContactsContract.Data.CONTENT_URI), arrayOf(CachedGroupMembership.GROUP_ID, CachedGroupMembership.RAW_CONTACT_ID),
            "${CachedGroupMembership.MIMETYPE}=?", arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE),
            null
        )!!.use { cursor ->
            assertFalse(cursor.moveToNext())
        }
    }


    @Test
    fun testMarkMembersDirty() {
        val ab = addressBookGroupsAsCategories
        val group = newGroup(ab)

        val contact1 = LocalContact(ab, Contact().apply { displayName = "Test" }, "fn.vcf", null, 0)
        contact1.add()

        val batch = BatchOperation(ab.provider!!)
        contact1.addToGroup(batch, group.id!!)
        batch.commit()

        assertEquals(0, ab.findDirty().size)
        group.markMembersDirty()
        assertEquals(contact1.id, ab.findDirty().first().id)
    }


    @Test
    fun testPrepareForUpload() {
        val group = newGroup()
        assertNull(group.getContact().uid)

        val fileName = group.prepareForUpload()
        val newUid = group.getContact().uid
        assertNotNull(newUid)
        assertEquals("$newUid.vcf", fileName)
    }

}