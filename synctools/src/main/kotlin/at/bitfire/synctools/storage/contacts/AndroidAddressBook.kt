/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Entity
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import androidx.core.content.contentValuesOf
import androidx.core.net.toUri
import at.bitfire.synctools.storage.contacts.AddressContract.asSyncAdapter
import at.bitfire.synctools.storage.contacts.AndroidAddressBook.Companion.USER_DATA_READ_ONLY
import at.bitfire.synctools.storage.toContentValues
import at.bitfire.synctools.util.setAndVerifyUserData
import at.bitfire.synctools.vcard.GroupMethod
import org.jetbrains.annotations.TestOnly
import java.io.FileNotFoundException
import java.io.IOException
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

class AndroidAddressBook(
    private val context: Context,
    var addressBookAccount: Account,
    val provider: ContentProviderClient
) {

    private val logger
        get() = Logger.getLogger(AndroidAddressBook::class.java.name)

    val accountManager
        get() = AccountManager.get(context)

    val groupMethod: GroupMethod = GroupMethod.GROUP_VCARDS

    /**
     * Read-only flag for the address book itself.
     *
     * Setting this flag:
     *
     * - stores the new value in [USER_DATA_READ_ONLY] and
     * - sets the read-only flag for all contacts and groups in the address book in the content provider, which will
     * prevent non-sync-adapter apps from modifying them. However new entries can still be created, so the address book
     * is not really read-only.
     *
     * Reading this flag returns the stored value from [USER_DATA_READ_ONLY].
     */
    var readOnly: Boolean
        get() = accountManager.getUserData(addressBookAccount, USER_DATA_READ_ONLY) != null
        set(readOnly) {
            accountManager.setAndVerifyUserData(addressBookAccount, USER_DATA_READ_ONLY, if (readOnly) "1" else null)

            // update raw contacts
            val rawContactValues = contentValuesOf(RawContacts.RAW_CONTACT_IS_READ_ONLY to if (readOnly) 1 else 0)
            provider.update(rawContactsSyncUri(), rawContactValues, null, null)

            // update data rows
            val dataValues = contentValuesOf(ContactsContract.Data.IS_READ_ONLY to if (readOnly) 1 else 0)
            provider.update(ContactsContract.Data.CONTENT_URI.asSyncAdapter(addressBookAccount), dataValues, null, null)

            // update group rows
            val groupValues = contentValuesOf(Groups.GROUP_IS_READ_ONLY to if (readOnly) 1 else 0)
            provider.update(groupsSyncUri(), groupValues, null, null)
        }

    var settings: ContentValues
        /**
         * Retrieves [ContactsContract.Settings] for the current address book.
         * @throws FileNotFoundException if the settings row couldn't be fetched.
         * @throws android.os.RemoteException on content provider errors
         */
        get() {
            provider.query(ContactsContract.Settings.CONTENT_URI.asSyncAdapter(addressBookAccount), null, null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.toContentValues()
            }
            throw FileNotFoundException()
        }

        /**
         * Updates [ContactsContract.Settings] by inserting the given values into
         * the current address book.
         * @param values settings to be updated
         * @throws android.os.RemoteException on content provider errors
         */
        set(values) {
            values.put(ContactsContract.Settings.ACCOUNT_NAME, addressBookAccount.name)
            values.put(ContactsContract.Settings.ACCOUNT_TYPE, addressBookAccount.type)
            provider.insert(ContactsContract.Settings.CONTENT_URI.asSyncAdapter(addressBookAccount), values)
        }

    var syncState: ByteArray?
        get() = ContactsContract.SyncState.get(provider, addressBookAccount)
        set(data) = ContactsContract.SyncState.set(provider, addressBookAccount, data)

    // ContactsContract.RawContacts CRUD

    fun addRawContact(rawContact: Entity): Long {
        TODO("Insert contact row + sub rows and return ID of the main row")
        // Make sure that new raw contact is assigned to addressBookAccount
        // (if necessary, create copy of Entity that sets the respective fields)
    }

    /**
     * Counts the number of contacts in the address book that match the given selection criteria.
     *
     * @param where An optional filter declaring which rows to return.
     * @param whereArgs Optional arguments for [where].
     * @return The number of contacts matching the selection criteria.
     */
    fun countRawContacts(where: String?, whereArgs: Array<String>?): Int {
        provider.query(
            rawContactsSyncUri(), arrayOf(RawContacts._ID),
            where, whereArgs, null)?.use { cursor ->
            return cursor.count
        }
        // If the query was invalid, an exception should have been thrown. So this should never be reached:
        return 0
    }

    fun iterateRawContacts(where: String?, whereArgs: Array<String>?, block: (Entity) -> Unit) {
        TODO()
    }

    // ContactsContract.Groups CRUD

    @Throws(FileNotFoundException::class)
    fun findGroupById(id: Long) =
        queryGroups("${Groups._ID}=?", arrayOf(id.toString())).firstOrNull() ?: throw FileNotFoundException()

    fun findOrCreateGroup(title: String): Long {
        provider.query(
            Groups.CONTENT_URI.asSyncAdapter(addressBookAccount), arrayOf(Groups._ID),
            "${Groups.TITLE}=?", arrayOf(title), null
        )?.use { cursor ->
            if (cursor.moveToNext())
                return cursor.getLong(0)
        }

        val values = contentValuesOf(Groups.TITLE to title)
        val uri = provider.insert(Groups.CONTENT_URI.asSyncAdapter(addressBookAccount), values)
            ?: throw RemoteException("Couldn't create contact group")
        return ContentUris.parseId(uri)
    }

    fun deleteGroupsWithoutMembers() {
        queryGroups(null, null).filter { it.getMembers().isEmpty() }.forEach { group ->
            logger.log(Level.FINE, "Deleting empty group", group)
            group.delete()
        }
    }

    // high-res photo access

    /**
     * Sets or clears the photo for a raw contact.
     *
     * When [photo] is non-null and decodable by [BitmapFactory], it is written to the contacts
     * provider via [RawContacts.DisplayPhoto.CONTENT_DIRECTORY]. The provider processes the image
     * asynchronously inside `PipeMonitor` using bare [ContactsContract.Data] URIs (without
     * `CALLER_IS_SYNCADAPTER`), which unconditionally marks the raw contact as dirty.
     * The method waits up to 7 seconds for processing to complete, then resets the dirty flag.
     * Invalid images (not decodable by [BitmapFactory]) are silently ignored.
     *
     * When [photo] is null, the existing [Photo.CONTENT_ITEM_TYPE] data row is deleted, which
     * removes both the thumbnail and the high-res display photo file.
     *
     * **Side effect: always resets [RawContacts.DIRTY] to 0** on the raw contact, regardless of
     * outcome, to counteract the async dirty mark set by the provider during photo processing.
     *
     * @param rawContactId  ID of the raw contact ([RawContacts._ID])
     * @param photo         contact photo in a supported format like JPEG or PNG, or null to delete
     */
    fun setPhoto(rawContactId: Long, photo: ByteArray?) {
        if (photo == null)
            provider.delete(
                /* url = */ ContactsContract.Data.CONTENT_URI.asSyncAdapter(addressBookAccount),
                /* selection = */ "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                /* selectionArgs = */ arrayOf(rawContactId.toString(), Photo.CONTENT_ITEM_TYPE)
            )
        else if (isValidPhoto(photo)) {
            writePhotoBytes(rawContactId, photo)
            val photoUri = awaitPhotoUri(rawContactId)
            if (photoUri != null)
                logger.log(Level.FINE, "Photo has been inserted: $photoUri")
            else
                logger.log(Level.WARNING, "Timeout when storing photo")
        } else
            logger.log(Level.WARNING, "Ignoring invalid contact photo")

        // reset dirty flag — see KDoc for why this is always needed
        provider.update(rawContactSyncUri(rawContactId), contentValuesOf(RawContacts.DIRTY to 0), null, null)
    }

    private fun isValidPhoto(photo: ByteArray): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(photo, 0, photo.size, opts)
        return opts.outHeight != -1 && opts.outWidth != -1
    }

    private fun writePhotoBytes(rawContactId: Long, photo: ByteArray) {
        val uri = RawContacts.CONTENT_URI.buildUpon()
            .appendPath(rawContactId.toString())
            .appendPath(RawContacts.DisplayPhoto.CONTENT_DIRECTORY)
            .build()
        logger.log(Level.FINE, "Writing photo to $uri (${photo.size} bytes)")
        provider.openAssetFile(uri, "w")?.use { fd ->
            try {
                fd.createOutputStream()?.use { os ->
                    os.write(photo)
                }
            } catch (e: IOException) {
                logger.log(Level.WARNING, "Couldn't store contact photo", e)
            }
        }
    }

    private fun awaitPhotoUri(rawContactId: Long): Uri? {
        val dataRowUri = RawContacts.CONTENT_URI.buildUpon()
            .appendPath(rawContactId.toString())
            .appendPath(RawContacts.Data.CONTENT_DIRECTORY)
            .build()
        (1..70).forEach { i ->
            // wait max. 70x100 ms = 7 seconds
            provider.query(dataRowUri, arrayOf(Photo.PHOTO_URI), "${RawContacts.Data.MIMETYPE}=?", arrayOf(Photo.CONTENT_ITEM_TYPE), null)?.use { cursor ->
                if (cursor.moveToNext())
                    cursor.getString(0)?.let { uriStr ->
                        return uriStr.toUri()
                    }
            }
            Thread.sleep(100)
        }
        return null
    }

    // legacy AndroidContact/AndroidGroup CRUD

    @Deprecated("Use iterateRawContacts instead")
    fun queryContacts(where: String?, whereArgs: Array<String>?): List<AndroidContact> {
        val contacts = LinkedList<AndroidContact>()
        provider.query(
            rawContactsSyncUri(), null,
                where, whereArgs, null)?.use { cursor ->
            while (cursor.moveToNext())
                contacts += AndroidContact(this, cursor.toContentValues())
        }
        return contacts
    }

    private fun queryGroups(where: String?, whereArgs: Array<String>?): List<AndroidGroup> {
        val groups = LinkedList<AndroidGroup>()
        provider.query(groupsSyncUri(), null, where, whereArgs, null)?.use { cursor ->
            while (cursor.moveToNext())
                groups += AndroidGroup(this, cursor.toContentValues())
        }
        return groups
    }

    @TestOnly
    @Throws(FileNotFoundException::class)
    fun findContactById(id: Long) =
            queryContacts("${RawContacts._ID}=?", arrayOf(id.toString())).firstOrNull() ?: throw FileNotFoundException()


    // helpers

    fun rawContactSyncUri(id: Long) = ContentUris.withAppendedId(RawContacts.CONTENT_URI, id).asSyncAdapter(addressBookAccount)
    fun rawContactsSyncUri() = RawContacts.CONTENT_URI.asSyncAdapter(addressBookAccount)
    fun groupsSyncUri() = Groups.CONTENT_URI.asSyncAdapter(addressBookAccount)


    companion object {

        /**
         * Indicates whether the address book is currently set to read-only (i.e. its contacts and groups have the read-only flag).
         *
         * User data of the address book account (Boolean).
         */
        const val USER_DATA_READ_ONLY = "read_only"

    }

}
