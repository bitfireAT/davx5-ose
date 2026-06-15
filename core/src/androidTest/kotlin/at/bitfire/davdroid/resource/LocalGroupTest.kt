/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.AddressContract.CachedGroupMembership
import at.bitfire.synctools.storage.contacts.AddressContract.asSyncAdapter
import at.bitfire.synctools.storage.contacts.ContactsBatchOperation
import at.bitfire.synctools.vcard.GroupMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Optional
import javax.inject.Inject

@HiltAndroidTest
class LocalGroupTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var localAddressBookFactory: LocalAddressBook.Factory

    lateinit var provider: ContentProviderClient

    val account = Account("Test Account", "Test Account Type")

    @Before
    fun setUp() {
        hiltRule.inject()

        val context = InstrumentationRegistry.getInstrumentation().context
        provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
    }

    @After
    fun tearDown() {
        provider.close()
    }

    @Test
    fun testClearDirty_addCachedGroupMembership() {
        LocalTestAddressBook.provide(account, provider, GroupMethod.CATEGORIES, localAddressBookFactory) { localAddressBook ->
            val group = newGroup(localAddressBook)

            val contact1 =
                LocalContact(localAddressBook, Contact().apply { displayName = "Test" }, "fn.vcf", null, 0)
            contact1.add()

            // insert group membership, but no cached group membership
            localAddressBook.ab.provider.insert(
                ContactsContract.Data.CONTENT_URI.asSyncAdapter(),
                contentValuesOf(
                    GroupMembership.MIMETYPE to GroupMembership.CONTENT_ITEM_TYPE,
                    GroupMembership.RAW_CONTACT_ID to contact1.id,
                    GroupMembership.GROUP_ROW_ID to group.id
                )
            )

            group.clearDirty(Optional.empty(), null)

            // check cached group membership
            localAddressBook.ab.provider.query(
                ContactsContract.Data.CONTENT_URI.asSyncAdapter(),
                arrayOf(CachedGroupMembership.GROUP_ID, CachedGroupMembership.RAW_CONTACT_ID),
                "${CachedGroupMembership.MIMETYPE}=?",
                arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE),
                null
            )!!.use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(group.id, cursor.getLong(0))
                assertEquals(contact1.id, cursor.getLong(1))

                assertFalse(cursor.moveToNext())
            }
        }
    }

    @Test
    fun testClearDirty_removeCachedGroupMembership() {
        LocalTestAddressBook.provide(account, provider, GroupMethod.CATEGORIES, localAddressBookFactory) { localAddressBook ->
            val group = newGroup(localAddressBook)

            val contact1 = LocalContact(localAddressBook, Contact().apply { displayName = "Test" }, "fn.vcf", null, 0)
            contact1.add()

            // insert cached group membership, but no group membership
            localAddressBook.ab.provider.insert(
                ContactsContract.Data.CONTENT_URI.asSyncAdapter(),
                contentValuesOf(
                    CachedGroupMembership.MIMETYPE to CachedGroupMembership.CONTENT_ITEM_TYPE,
                    CachedGroupMembership.RAW_CONTACT_ID to contact1.id,
                    CachedGroupMembership.GROUP_ID to group.id
                )
            )

            group.clearDirty(Optional.empty(), null)

            // cached group membership should be gone
            localAddressBook.ab.provider.query(
                ContactsContract.Data.CONTENT_URI.asSyncAdapter(), arrayOf(CachedGroupMembership.GROUP_ID, CachedGroupMembership.RAW_CONTACT_ID),
                "${CachedGroupMembership.MIMETYPE}=?", arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE),
                null
            )!!.use { cursor ->
                assertFalse(cursor.moveToNext())
            }
            }
    }

    @Test
    fun testMarkMembersDirty() {
        LocalTestAddressBook.provide(account, provider, GroupMethod.CATEGORIES, localAddressBookFactory) { localAddressBook ->
            val group = newGroup(localAddressBook)

            val contact1 =
                LocalContact(localAddressBook, Contact().apply { displayName = "Test" }, "fn.vcf", null, 0)
            contact1.add()

            val batch = ContactsBatchOperation(localAddressBook.ab.provider)
            contact1.addToGroup(batch, group.id!!)
            batch.commit()

            assertEquals(0, localAddressBook.findDirty().size)
            group.markMembersDirty()
            assertEquals(contact1.id, localAddressBook.findDirty().first().id)
        }
    }

    @Test
    fun testUpdate() {
        LocalTestAddressBook.provide(account, provider, factory = localAddressBookFactory) {
            val group = newGroup(it)
            group.update(Contact(displayName = "New Group Name"), null, null, null, 0)
        }
    }


    // helpers

    private fun newGroup(addressBook: LocalAddressBook): LocalGroup =
        LocalGroup(addressBook,
            Contact().apply {
                displayName = "Test Group"
            }, null, null, 0
        ).apply {
            add()
        }

}