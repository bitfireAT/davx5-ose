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


    // iterateRawContacts

    @Test
    fun testIterateRawContacts_empty() {
        val addressBook = TestAddressBook.create(provider)
        try {
            var count = 0
            addressBook.iterateRawContacts { count++ }
            assertEquals(0, count)
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testIterateRawContacts_all() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Iterate Contact 1")))
            val id2 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Iterate Contact 2")))
            try {
                val ids = mutableListOf<Long>()
                addressBook.iterateRawContacts { entity ->
                    ids += entity.entityValues.getAsLong(RawContacts._ID)
                }
                assertEquals(setOf(id1, id2), ids.toSet())
            } finally {
                provider.delete(addressBook.rawContactSyncUri(id1), null, null)
                provider.delete(addressBook.rawContactSyncUri(id2), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testIterateRawContacts_filter() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.addRawContact(Entity(contentValuesOf(
                RawContacts.DISPLAY_NAME_PRIMARY to "Iterate Filter 1",
                AddressContract.RawContactColumns.UID to "iter-uid-1"
            )))
            val id2 = addressBook.addRawContact(Entity(contentValuesOf(
                RawContacts.DISPLAY_NAME_PRIMARY to "Iterate Filter 2",
                AddressContract.RawContactColumns.UID to "iter-uid-2"
            )))
            try {
                val ids = mutableListOf<Long>()
                addressBook.iterateRawContacts("${AddressContract.RawContactColumns.UID}=?", arrayOf("iter-uid-1")) { entity ->
                    ids += entity.entityValues.getAsLong(RawContacts._ID)
                }
                assertEquals(listOf(id1), ids)
            } finally {
                provider.delete(addressBook.rawContactSyncUri(id1), null, null)
                provider.delete(addressBook.rawContactSyncUri(id2), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }


    // iterateRawContactRows

    @Test
    fun testIterateRawContactRows_empty() {
        val addressBook = TestAddressBook.create(provider)
        try {
            var count = 0
            addressBook.iterateRawContactRows { count++ }
            assertEquals(0, count)
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testIterateRawContactRows_all() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Row Contact 1")))
            val id2 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Row Contact 2")))
            try {
                val ids = mutableListOf<Long>()
                addressBook.iterateRawContactRows { values ->
                    ids += values.getAsLong(RawContacts._ID)
                }
                assertEquals(setOf(id1, id2), ids.toSet())
            } finally {
                provider.delete(addressBook.rawContactSyncUri(id1), null, null)
                provider.delete(addressBook.rawContactSyncUri(id2), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testIterateRawContactRows_filter() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.addRawContact(Entity(contentValuesOf(
                RawContacts.DISPLAY_NAME_PRIMARY to "Row Filter 1",
                AddressContract.RawContactColumns.UID to "row-uid-1"
            )))
            val id2 = addressBook.addRawContact(Entity(contentValuesOf(
                RawContacts.DISPLAY_NAME_PRIMARY to "Row Filter 2",
                AddressContract.RawContactColumns.UID to "row-uid-2"
            )))
            try {
                val ids = mutableListOf<Long>()
                addressBook.iterateRawContactRows("${AddressContract.RawContactColumns.UID}=?", arrayOf("row-uid-1")) { values ->
                    ids += values.getAsLong(RawContacts._ID)
                }
                assertEquals(listOf(id1), ids)
            } finally {
                provider.delete(addressBook.rawContactSyncUri(id1), null, null)
                provider.delete(addressBook.rawContactSyncUri(id2), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }


    // updateRawContactRows

    @Test
    fun testUpdateRawContactRows_all() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Update Contact 1")))
            val id2 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Update Contact 2")))
            try {
                val batch = ContactsBatchOperation(provider)
                addressBook.updateRawContactRows(contentValuesOf(AddressContract.RawContactColumns.UID to "updated-uid"), null, null, batch)
                batch.commit()

                val uids = mutableListOf<String>()
                addressBook.iterateRawContactRows { values ->
                    uids += values.getAsString(AddressContract.RawContactColumns.UID)
                }
                assertEquals(2, uids.count { it == "updated-uid" })
            } finally {
                provider.delete(addressBook.rawContactSyncUri(id1), null, null)
                provider.delete(addressBook.rawContactSyncUri(id2), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testUpdateRawContactRows_filter() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Update Filter 1")))
            val id2 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Update Filter 2")))
            try {
                val batch = ContactsBatchOperation(provider)
                addressBook.updateRawContactRows(
                    contentValuesOf(AddressContract.RawContactColumns.UID to "only-id1"),
                    "${RawContacts._ID}=?", arrayOf(id1.toString()),
                    batch
                )
                batch.commit()

                var uid1: String? = "not-set"
                var uid2: String? = "not-set"
                addressBook.iterateRawContactRows { values ->
                    when (values.getAsLong(RawContacts._ID)) {
                        id1 -> uid1 = values.getAsString(AddressContract.RawContactColumns.UID)
                        id2 -> uid2 = values.getAsString(AddressContract.RawContactColumns.UID)
                    }
                }
                assertEquals("only-id1", uid1)
                assertNull(uid2)
            } finally {
                provider.delete(addressBook.rawContactSyncUri(id1), null, null)
                provider.delete(addressBook.rawContactSyncUri(id2), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }


    // iterateGroups

    @Test
    fun testIterateGroups_empty() {
        val addressBook = TestAddressBook.create(provider)
        try {
            var count = 0
            addressBook.iterateGroups { count++ }
            assertEquals(0, count)
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testIterateGroups_all() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.findOrCreateGroup("Iterate Group 1")
            val id2 = addressBook.findOrCreateGroup("Iterate Group 2")
            try {
                val ids = mutableListOf<Long>()
                addressBook.iterateGroups { values ->
                    ids += values.getAsLong(Groups._ID)
                }
                assertEquals(setOf(id1, id2), ids.toSet())
            } finally {
                provider.delete(addressBook.groupsSyncUri(), "${Groups._ID}=?", arrayOf(id1.toString()))
                provider.delete(addressBook.groupsSyncUri(), "${Groups._ID}=?", arrayOf(id2.toString()))
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testIterateGroups_filter() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.findOrCreateGroup("Iterate Filter Group 1")
            val id2 = addressBook.findOrCreateGroup("Iterate Filter Group 2")
            try {
                val ids = mutableListOf<Long>()
                addressBook.iterateGroups(null, "${Groups._ID}=?", arrayOf(id1.toString())) { values ->
                    ids += values.getAsLong(Groups._ID)
                }
                assertEquals(listOf(id1), ids)
            } finally {
                provider.delete(addressBook.groupsSyncUri(), "${Groups._ID}=?", arrayOf(id1.toString()))
                provider.delete(addressBook.groupsSyncUri(), "${Groups._ID}=?", arrayOf(id2.toString()))
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }


    // updateGroups

    @Test
    fun testUpdateGroups_all() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.findOrCreateGroup("Update Group 1")
            val id2 = addressBook.findOrCreateGroup("Update Group 2")
            try {
                val batch = ContactsBatchOperation(provider)
                addressBook.updateGroups(contentValuesOf(AddressContract.GroupColumns.ETAG to "updated-etag"), null, null, batch)
                batch.commit()

                var count = 0
                provider.query(addressBook.groupsSyncUri(), arrayOf(AddressContract.GroupColumns.ETAG), null, null, null)!!.use { cursor ->
                    while (cursor.moveToNext())
                        if (cursor.getString(0) == "updated-etag") count++
                }
                assertEquals(2, count)
            } finally {
                provider.delete(addressBook.groupsSyncUri(), "${Groups._ID}=?", arrayOf(id1.toString()))
                provider.delete(addressBook.groupsSyncUri(), "${Groups._ID}=?", arrayOf(id2.toString()))
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testUpdateGroups_filter() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.findOrCreateGroup("Filter Group 1")
            val id2 = addressBook.findOrCreateGroup("Filter Group 2")
            try {
                val batch = ContactsBatchOperation(provider)
                addressBook.updateGroups(
                    contentValuesOf(AddressContract.GroupColumns.ETAG to "only-group1"),
                    "${Groups._ID}=?", arrayOf(id1.toString()),
                    batch
                )
                batch.commit()

                var etag1: String? = null
                var etag2: String? = "not-set"
                provider.query(addressBook.groupsSyncUri(), arrayOf(Groups._ID, AddressContract.GroupColumns.ETAG), null, null, null)!!.use { cursor ->
                    while (cursor.moveToNext()) {
                        when (cursor.getLong(0)) {
                            id1 -> etag1 = cursor.getString(1)
                            id2 -> etag2 = cursor.getString(1)
                        }
                    }
                }
                assertEquals("only-group1", etag1)
                assertNull(etag2)
            } finally {
                provider.delete(addressBook.groupsSyncUri(), "${Groups._ID}=?", arrayOf(id1.toString()))
                provider.delete(addressBook.groupsSyncUri(), "${Groups._ID}=?", arrayOf(id2.toString()))
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }


    // deleteGroups

    @Test
    fun testDeleteGroups_all() {
        val addressBook = TestAddressBook.create(provider)
        try {
            addressBook.findOrCreateGroup("Delete Group 1")
            addressBook.findOrCreateGroup("Delete Group 2")

            val batch = ContactsBatchOperation(provider)
            addressBook.deleteGroups(null, null, batch)
            batch.commit()

            provider.query(addressBook.groupsSyncUri(), arrayOf(Groups._ID), null, null, null)!!.use { cursor ->
                assertEquals(0, cursor.count)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testDeleteGroups_filter() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.findOrCreateGroup("Delete Filter Group 1")
            val id2 = addressBook.findOrCreateGroup("Delete Filter Group 2")
            try {
                val batch = ContactsBatchOperation(provider)
                addressBook.deleteGroups("${Groups._ID}=?", arrayOf(id1.toString()), batch)
                batch.commit()

                provider.query(addressBook.groupsSyncUri(), arrayOf(Groups._ID), null, null, null)!!.use { cursor ->
                    assertEquals(1, cursor.count)
                    assertTrue(cursor.moveToNext())
                    assertEquals(id2, cursor.getLong(0))
                }
            } finally {
                provider.delete(addressBook.groupsSyncUri(), "${Groups._ID}=?", arrayOf(id1.toString()))
                provider.delete(addressBook.groupsSyncUri(), "${Groups._ID}=?", arrayOf(id2.toString()))
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }


    // deleteRawContacts

    @Test
    fun testDeleteRawContacts_all() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Delete Contact 1")))
            val id2 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Delete Contact 2")))
            try {
                val batch = ContactsBatchOperation(provider)
                addressBook.deleteRawContacts(null, null, batch)
                batch.commit()

                assertEquals(0, addressBook.countRawContacts(null, null))
            } finally {
                provider.delete(addressBook.rawContactSyncUri(id1), null, null)
                provider.delete(addressBook.rawContactSyncUri(id2), null, null)
            }
        } finally {
            TestAddressBook.remove(addressBook)
        }
    }

    @Test
    fun testDeleteRawContacts_filter() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val id1 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Delete Filter 1")))
            val id2 = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Delete Filter 2")))
            try {
                val batch = ContactsBatchOperation(provider)
                addressBook.deleteRawContacts("${RawContacts._ID}=?", arrayOf(id1.toString()), batch)
                batch.commit()

                assertEquals(1, addressBook.countRawContacts(null, null))
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


    @Test
    fun testSetPhoto_ReadOnly_Update() {
        val addressBook = TestAddressBook.create(provider)
        try {
            val rawContactId = addressBook.addRawContact(Entity(contentValuesOf(RawContacts.DISPLAY_NAME_PRIMARY to "Read-only photo contact")))
            try {
                // Set initial photo so that a photo data row exists.
                addressBook.setPhoto(rawContactId, TestUtils.resourceToByteArray("/large.jpg"))
                val photo1 = addressBook.findContactById(rawContactId).getContact().photo
                assertNotNull("Initial photo should be set", photo1)

                // Mark address book as read-only: stamps IS_READ_ONLY=1 on the photo data row.
                addressBook.readOnly = true

                // Updating the photo on a read-only contact must still succeed.
                addressBook.setPhoto(rawContactId, TestUtils.resourceToByteArray("/small.jpg"))
                val photo2 = addressBook.findContactById(rawContactId).getContact().photo
                assertNotNull("Photo should still be present after update", photo2)
                assertFalse("Photo should have been updated despite read-only flag", photo1!!.contentEquals(photo2!!))
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
