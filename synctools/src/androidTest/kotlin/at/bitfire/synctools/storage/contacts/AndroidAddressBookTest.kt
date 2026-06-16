/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.Manifest
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.mapping.contacts.TestUtils
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class AndroidAddressBookTest {

    companion object {
        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

        private lateinit var provider: ContentProviderClient

        @BeforeClass
        @JvmStatic
        fun connect() {
            val context = InstrumentationRegistry.getInstrumentation().context
            provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
            assertNotNull(provider)
        }

        @AfterClass
        @JvmStatic
        fun disconnect() {
            @Suppress("DEPRECATION")
            provider.release()
        }
    }


    @Test
    fun testCountRawContacts_empty() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val count = addressBook.countRawContacts(null, null)
            assertEquals(0, count)
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testCountContacts_withRawContacts() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Test Contact 1")))
            val id2 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Test Contact 2")))
            try {
                val count = addressBook.countRawContacts(null, null)
                assertEquals(2, count)
            } finally {
                provider.delete(addressBook.rawContactSyncUri(id1), null, null)
                provider.delete(addressBook.rawContactSyncUri(id2), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testCountRawContacts_withFilter() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.addRawContact(
                Entity(
                    contentValuesOf(
                        RawContacts.DISPLAY_NAME_PRIMARY to "Filter Test 1",
                        AddressContract.RawContactColumns.UID to "test-uid-1"
                    )
                )
            )
            val id2 = addressBook.addRawContact(
                Entity(
                    contentValuesOf(
                        RawContacts.DISPLAY_NAME_PRIMARY to "Filter Test 2",
                        AddressContract.RawContactColumns.UID to "test-uid-2"
                    )
                )
            )
            try {
                val filteredCount = addressBook.countRawContacts("${AddressContract.RawContactColumns.UID}=?", arrayOf("test-uid-1"))
                assertEquals(1, filteredCount)

                val noMatchCount = addressBook.countRawContacts("${AddressContract.RawContactColumns.UID}=?", arrayOf("non-existent"))
                assertEquals(0, noMatchCount)
            } finally {
                provider.delete(addressBook.rawContactSyncUri(id1), null, null)
                provider.delete(addressBook.rawContactSyncUri(id2), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }


    @Test
    fun testSettings() {
        val addressBook = TestAddressBook.create(provider)
        try {
            var values = ContentValues()
            values.put(ContactsContract.Settings.SHOULD_SYNC, false)
            values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, false)
            addressBook.settings = values
            values = addressBook.settings
            assertFalse(values.getAsInteger(ContactsContract.Settings.SHOULD_SYNC) != 0)
            assertFalse(values.getAsInteger(ContactsContract.Settings.UNGROUPED_VISIBLE) != 0)

            values = ContentValues()
            values.put(ContactsContract.Settings.SHOULD_SYNC, true)
            values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, true)
            addressBook.settings = values
            values = addressBook.settings
            assertTrue(values.getAsInteger(ContactsContract.Settings.SHOULD_SYNC) != 0)
            assertTrue(values.getAsInteger(ContactsContract.Settings.UNGROUPED_VISIBLE) != 0)
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testSyncState() {
        val addressBook = TestAddressBook.create(provider)
        try {
            addressBook.syncState = ByteArray(0)
            assertEquals(0, addressBook.syncState!!.size)

            val random = byteArrayOf(1, 2, 3, 4, 5)
            addressBook.syncState = random
            assertArrayEquals(random, addressBook.syncState)
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }


    // setPhoto

    @Test
    fun testSetPhoto() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val rawContactId = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Contact with photo")))
            try {
                val photo = TestUtils.resourceToByteArray("/large.jpg")
                addressBook.setPhoto(rawContactId, photo)

                // the photo is processed and often resized by the contacts provider
                val photo2 = addressBook.findContactById(rawContactId).getContact().photo!!

                // verify that the image is in JPEG format (some Samsung devices seem to save as PNG)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(photo2, 0, photo2.size, options)
                assertEquals("image/jpeg", options.outMimeType)

                // verify that contact is not dirty
                provider.query(
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                    arrayOf(RawContacts.DIRTY),
                    null, null, null
                )!!.use { cursor ->
                    assertTrue(cursor.moveToNext())
                    assertEquals(0, cursor.getInt(0))
                }
            } finally {
                provider.delete(addressBook.rawContactSyncUri(rawContactId), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testSetPhoto_Invalid() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val rawContactId = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Contact with invalid photo")))
            try {
                addressBook.setPhoto(rawContactId, ByteArray(100) /* invalid photo */)
                // no exception; photo remains absent
                assertNull(addressBook.findContactById(rawContactId).getContact().photo)
            } finally {
                provider.delete(addressBook.rawContactSyncUri(rawContactId), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testSetPhoto_Null_DeletesPhoto() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val rawContactId = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Contact photo delete")))
            try {
                // set a valid photo first
                addressBook.setPhoto(rawContactId, TestUtils.resourceToByteArray("/large.jpg"))
                assertNotNull(addressBook.findContactById(rawContactId).getContact().photo)

                // now clear it
                addressBook.setPhoto(rawContactId, null)
                assertNull(addressBook.findContactById(rawContactId).getContact().photo)

                // contact must not be dirty
                provider.query(
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                    arrayOf(RawContacts.DIRTY),
                    null, null, null
                )!!.use { cursor ->
                    assertTrue(cursor.moveToNext())
                    assertEquals(0, cursor.getInt(0))
                }
            } finally {
                provider.delete(addressBook.rawContactSyncUri(rawContactId), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }


    // deleteGroupsWithoutMembers

    @Test
    fun testDeleteGroupsWithoutMembers_deletesEmpty() {
        val addressBook = TestAddressBook.create(provider)
        try {
            addressBook.findOrCreateGroup("Empty Group")

            addressBook.deleteGroupsWithoutMembers()

            provider.query(addressBook.groupsSyncUri(), arrayOf(Groups._ID), null, null, null)!!.use { cursor ->
                assertEquals(0, cursor.count)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testDeleteGroupsWithoutMembers_keepsNonEmpty() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val groupId = addressBook.findOrCreateGroup("Group with member")
            val rawContactId = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Member")))
            try {
                val batch = ContactsBatchOperation(provider)
                addressBook.findContactById(rawContactId).addToGroup(batch, groupId)
                batch.commit()

                addressBook.deleteGroupsWithoutMembers()

                provider.query(addressBook.groupsSyncUri(), arrayOf(Groups._ID), null, null, null)!!.use { cursor ->
                    assertEquals(1, cursor.count)
                }
            } finally {
                provider.delete(addressBook.rawContactSyncUri(rawContactId), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

}
