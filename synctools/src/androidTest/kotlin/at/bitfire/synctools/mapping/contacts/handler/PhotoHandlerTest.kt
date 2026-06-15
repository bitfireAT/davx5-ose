/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.Manifest
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.RawContacts
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.TestUtils
import at.bitfire.synctools.storage.contacts.AndroidAddressBook
import at.bitfire.synctools.storage.contacts.AndroidContact
import at.bitfire.synctools.storage.contacts.TestAddressBook
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.util.Arrays

class PhotoHandlerTest {

    companion object {
        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

        val testContext = InstrumentationRegistry.getInstrumentation().context
        private lateinit var provider: ContentProviderClient
        private lateinit var addressBook: AndroidAddressBook

        @BeforeClass
        @JvmStatic
        fun connect() {
            provider = testContext.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
            assertNotNull(provider)

            addressBook = TestAddressBook.create(provider)
        }

        @AfterClass
        @JvmStatic
        fun disconnect() {
            TestAddressBook.remove(addressBook)
            @Suppress("DEPRECATION")
            provider.release()
        }
    }


    @Test
    fun testConvertToJpeg_Invalid() {
        val blob = ByteArray(1024) { it.toByte() }
        assertNull(PhotoHandler.convertToJpeg(blob, 75))
    }

    @Test
    fun testConvertToJpeg_Jpeg() {
        val blob = TestUtils.resourceToByteArray("/small.jpg")
        assertArrayEquals(blob, PhotoHandler.convertToJpeg(blob, 75))
    }

    @Test
    fun testConvertToJpeg_Png() {
        val blob = TestUtils.resourceToByteArray("/small.png")
        assertFalse(Arrays.equals(blob, PhotoHandler.convertToJpeg(blob, 75)))
    }


    @Test
    fun testPhoto_Empty() {
        val contact = Contact()
        PhotoHandler(null).handle(ContentValues().apply {
            putNull(Photo.PHOTO)
        }, contact)
        assertNull(contact.photo)
    }

    @Test
    fun testPhoto_Blob() {
        val blob = TestUtils.resourceToByteArray("/small.jpg")
        val contact = Contact()
        PhotoHandler(null).handle(ContentValues().apply {
            put(Photo.PHOTO, blob)
        }, contact)
        assertEquals(blob, contact.photo)
    }

    @Test
    fun testPhoto_FileId() {
        val contact = Contact().apply {
            displayName = "Contact with photo"
            photo = TestUtils.resourceToByteArray("/large.jpg")
        }
        val androidContact = AndroidContact(addressBook, contact, null, null)
        val rawContactId = ContentUris.parseId(androidContact.add())

        val dataUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId).buildUpon()
            .appendPath(RawContacts.Data.CONTENT_DIRECTORY)
            .build()
        val thumbnail = provider.query(dataUri, arrayOf(Photo.PHOTO_FILE_ID, Photo.PHOTO),
            "${RawContacts.Data.MIMETYPE}=?", arrayOf(Photo.CONTENT_ITEM_TYPE),
            null
        )!!.use { cursor ->
            assertTrue(cursor.moveToNext())

            val fileId = cursor.getLong(0)
            assertNotNull(fileId)

            val blob = cursor.getBlob(1)
            assertNotNull(blob)
            blob!!
        }

        val contact2 = addressBook.findContactById(rawContactId)
        // now PhotoHandler handles the PHOTO_FILE_ID
        val photo2 = contact2.getContact().photo
        assertNotNull(photo2)
        // make sure PhotoHandler didn't just return the thumbnail blob
        assertNotEquals(thumbnail, photo2)
    }

}
