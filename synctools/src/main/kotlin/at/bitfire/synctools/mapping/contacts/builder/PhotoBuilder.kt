/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.RawContacts
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.contacts.ContactContract.asSyncAdapter
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

class PhotoBuilder(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean)
    : DataRowBuilder(Factory.mimeType(), dataRowUri, rawContactId, contact, readOnly) {

    companion object {

        private val logger
            get() = Logger.getLogger(PhotoBuilder::class.java.name)

        /**
         * Inserts a raw contact photo and resets [RawContacts.DIRTY] to 0 then.
         *
         * If the contact provider needs more than 7 seconds to insert the photo, this
         * method will time out and log the failure as a warning. In this case, the
         * [RawContacts.DIRTY] flag may be set asynchronously by the contacts provider
         * as soon as it finishes the operation.
         *
         * @param provider       client to access contacts provider
         * @param rawContactId   ID of the raw contact ([RawContacts._ID]])
         * @param data           contact photo (binary data in a supported format like JPEG or PNG)
         *
         * @return URI of the raw contact display photo ([Photo.PHOTO_URI]); null if image can't be decoded
         */
        fun insertPhoto(provider: ContentProviderClient, rawContactId: Long, data: ByteArray): Uri? {
            // verify that data can be decoded by BitmapFactory, so that the contacts provider can process it
            val opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(data, 0, data.size, opts)
            val valid = opts.outHeight != -1 && opts.outWidth != -1
            if (!valid) {
                logger.log(Level.WARNING, "Ignoring invalid contact photo")
                return null
            }

            // write file to contacts provider
            val uri = RawContacts.CONTENT_URI.buildUpon()
                .appendPath(rawContactId.toString())
                .appendPath(RawContacts.DisplayPhoto.CONTENT_DIRECTORY)
                .build()
            logger.log(Level.FINE, "Writing photo to $uri (${data.size} bytes)")
            provider.openAssetFile(uri, "w")?.use { fd ->
                try {
                    fd.createOutputStream()?.use { os ->
                        os.write(data)
                    }
                } catch (e: IOException) {
                    logger.log(Level.WARNING, "Couldn't store contact photo", e)
                }
            }

            // photo is now processed in the background; wait until it is available
            var photoUri: Uri? = null
            for (i in 1..70) {      // wait max. 70x100 ms = 7 seconds
                val dataRowUri = RawContacts.CONTENT_URI.buildUpon()
                    .appendPath(rawContactId.toString())
                    .appendPath(RawContacts.Data.CONTENT_DIRECTORY)
                    .build()
                provider.query(dataRowUri, arrayOf(Photo.PHOTO_URI), "${RawContacts.Data.MIMETYPE}=?", arrayOf(Photo.CONTENT_ITEM_TYPE), null)?.use { cursor ->
                    if (cursor.moveToNext())
                        cursor.getString(0)?.let { uriStr ->
                            photoUri = Uri.parse(uriStr)
                        }
                }
                if (photoUri != null)
                    break
                Thread.sleep(100)
            }

            // reset dirty flag in any case (however if we didn't wait long enough, the dirty flag will then be set again)
            val notDirty = ContentValues(1)
            notDirty.put(RawContacts.DIRTY, 0)
            val rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId).asSyncAdapter()
            provider.update(rawContactUri, notDirty, null, null)

            if (photoUri != null)
                logger.log(Level.FINE, "Photo has been inserted: $photoUri")
            else
                logger.log(Level.WARNING, "Timeout when storing photo")

            return photoUri
        }

    }


    override fun build(): List<BatchOperation.CpoBuilder> =
        emptyList()     // data row must be inserted by calling insertPhoto()


    object Factory: DataRowBuilder.Factory<PhotoBuilder> {
        override fun mimeType() = Photo.CONTENT_ITEM_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) =
            PhotoBuilder(dataRowUri, rawContactId, contact, readOnly)
    }

}