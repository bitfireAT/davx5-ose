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
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.contacts.AddressContract.asSyncAdapter
import at.bitfire.synctools.storage.contacts.AndroidAddressBook.Companion.USER_DATA_READ_ONLY
import at.bitfire.synctools.storage.queryFlow
import at.bitfire.synctools.storage.toContentValues
import at.bitfire.synctools.util.setAndVerifyUserData
import at.bitfire.synctools.vcard.GroupMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.FileNotFoundException
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Represents an Android address book backed by the contacts content provider for a given
 * [addressBookAccount] (operations are restricted to that account).
 *
 * Provides CRUD operations for raw contacts and groups, always operating "as sync adapter"
 * so changes bypass the dirty-flag and read-only restrictions.
 *
 * @param context            application context (used to obtain the [AccountManager])
 * @param addressBookAccount account whose contacts and groups are managed
 * @param provider           content provider client for [ContactsContract]
 */
class AndroidAddressBook(
    private val context: Context,
    var addressBookAccount: Account,
    val provider: ContentProviderClient
) {

    private val logger
        get() = Logger.getLogger(AndroidAddressBook::class.java.name)

    private val accountManager: AccountManager
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

    // region ContactsContract.RawContacts CRUD

    /**
     * Adds a raw contact and its associated data to the address book.
     *
     * This method operates "as sync adapter" on [addressBookAccount] and doesn't take the [readOnly] flag into account.
     *
     * @param rawContact The raw contact entity to add, containing main values and sub-values.
     * @return The ID of the newly created raw contact.
     * @throws LocalStorageException If the contact cannot be inserted.
     */
    fun addRawContact(rawContact: Entity): Long {
        try {
            val batch = ContactsBatchOperation(provider)

            val rawContactValues = ContentValues(rawContact.entityValues).apply {
                remove(RawContacts._ID)
                put(RawContacts.ACCOUNT_NAME, addressBookAccount.name)
                put(RawContacts.ACCOUNT_TYPE, addressBookAccount.type)
            }
            batch += BatchOperation.CpoBuilder
                .newInsert(rawContactsSyncUri())
                .withValues(rawContactValues)

            for (subValue in rawContact.subValues) {
                val dataValues = ContentValues(subValue.values).apply {
                    remove(ContactsContract.Data._ID)
                }
                batch += BatchOperation.CpoBuilder
                    .newInsert(ContactsContract.Data.CONTENT_URI.asSyncAdapter())
                    .withValues(dataValues)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            }

            batch.commit()
            val uri = batch.getResult(0)?.uri ?: throw LocalStorageException("Content provider returned null on insert")
            return ContentUris.parseId(uri)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't insert raw contact", e)
        }
    }

    /**
     * Counts the number of contacts in the address book that match the given selection criteria.
     *
     * This method operates "as sync adapter" on [addressBookAccount] and doesn't take the [readOnly] flag into account.
     *
     * @param where An optional filter declaring which rows to return.
     * @param whereArgs Optional arguments for [where].
     * @return The number of contacts matching the selection criteria.
     */
    fun countRawContacts(where: String?, whereArgs: Array<String>?): Int {
        // account is implicitly restricted via the URI (asSyncAdapter appends ACCOUNT_NAME/ACCOUNT_TYPE)
        provider.query(
            rawContactsSyncUri(), arrayOf(RawContacts._ID),
            where, whereArgs, null
        )?.use { cursor ->
            return cursor.count
        }
        throw LocalStorageException("Couldn't count raw contacts")
    }

    /**
     * Cold [Flow] of raw contact rows (without associated data rows) from this address book.
     *
     * This method operates "as sync adapter" on [addressBookAccount] and doesn't take the [readOnly] flag into account.
     *
     * @param where      optional selection
     * @param whereArgs  optional arguments for [where]
     */
    fun queryRawContactRows(where: String? = null, whereArgs: Array<String>? = null): Flow<ContentValues> =
        provider.queryFlow(rawContactsSyncUri(), null, where, whereArgs)

    /**
     * Finds the first raw contact row matching the given selection, without collecting
     * the full result set.
     *
     * This method operates "as sync adapter" on [addressBookAccount] and doesn't take the [readOnly] flag into account.
     *
     * @param where      optional selection
     * @param whereArgs  optional arguments for [where]
     * @return the matching row, or `null` if none matches
     */
    fun getRawContactRowOrNull(where: String?, whereArgs: Array<String>?): ContentValues? {
        provider.query(rawContactsSyncUri(), null, where, whereArgs, null)?.use { cursor ->
            if (cursor.moveToNext())
                return cursor.toContentValues()
        }
        return null
    }

    /**
     * Enqueues an update of raw contact rows in this address book to the given batch.
     *
     * This method operates "as sync adapter" on [addressBookAccount] and doesn't take the [readOnly] flag into account.
     *
     * @param values    values to update
     * @param where     optional selection
     * @param whereArgs optional arguments for [where]
     * @param batch     batch operation to enqueue the update into
     */
    fun updateRawContactRows(values: ContentValues, where: String? = null, whereArgs: Array<String>? = null, batch: ContactsBatchOperation) {
        // account is implicitly restricted via the URI (asSyncAdapter appends ACCOUNT_NAME/ACCOUNT_TYPE)
        val builder = BatchOperation.CpoBuilder
            .newUpdate(rawContactsSyncUri())
            .withValues(values)
        if (where != null)
            builder.withSelection(where, whereArgs ?: emptyArray())
        batch += builder
    }

    /**
     * Enqueues a deletion of raw contacts in this address book to the given batch.
     *
     * This method operates "as sync adapter" on [addressBookAccount] and doesn't take the [readOnly] flag into account.
     *
     * @param where     optional selection
     * @param whereArgs optional arguments for [where]
     * @param batch     batch operation to enqueue the deletion into
     */
    fun deleteRawContacts(where: String? = null, whereArgs: Array<String>? = null, batch: ContactsBatchOperation) {
        // account is implicitly restricted via the URI (asSyncAdapter appends ACCOUNT_NAME/ACCOUNT_TYPE)
        val builder = BatchOperation.CpoBuilder
            .newDelete(rawContactsSyncUri())
        if (where != null)
            builder.withSelection(where, whereArgs ?: emptyArray())
        batch += builder
    }

    // endregion

    // region ContactsContract.Groups CRUD

    @Throws(FileNotFoundException::class)
    fun findGroupById(id: Long): AndroidGroup =
        // account is implicitly restricted via the URI (asSyncAdapter appends ACCOUNT_NAME/ACCOUNT_TYPE)
        getGroupOrNull("${Groups._ID}=?", arrayOf(id.toString()))
            ?.let { AndroidGroup(this, it) }
            ?: throw FileNotFoundException()

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

    /**
     * Cold [Flow] of group rows in this address book.
     *
     * This method operates "as sync adapter" on [addressBookAccount] and doesn't take the [readOnly] flag into account.
     *
     * @param projection optional column projection
     * @param where      optional selection
     * @param whereArgs  optional arguments for [where]
     */
    fun queryGroupRows(
        projection: Array<String>? = null,
        where: String? = null,
        whereArgs: Array<String>? = null
    ): Flow<ContentValues> =
        provider.queryFlow(groupsSyncUri(), projection, where, whereArgs)

    /**
     * Finds the first group row matching the given selection, without collecting
     * the full result set.
     *
     * This method operates "as sync adapter" on [addressBookAccount] and doesn't take the [readOnly] flag into account.
     *
     * @param where      optional selection
     * @param whereArgs  optional arguments for [where]
     * @return the matching row, or `null` if none matches
     */
    fun getGroupOrNull(where: String?, whereArgs: Array<String>?): ContentValues? {
        provider.query(groupsSyncUri(), null, where, whereArgs, null)?.use { cursor ->
            if (cursor.moveToNext())
                return cursor.toContentValues()
        }
        return null
    }

    /**
     * Counts the number of groups in the address book that match the given selection criteria.
     *
     * This method operates "as sync adapter" on [addressBookAccount] and doesn't take the [readOnly] flag into account.
     *
     * @param where An optional filter declaring which rows to return.
     * @param whereArgs Optional arguments for [where].
     * @return The number of groups matching the selection criteria.
     */
    fun countGroups(where: String?, whereArgs: Array<String>?): Int {
        // account is implicitly restricted via the URI (asSyncAdapter appends ACCOUNT_NAME/ACCOUNT_TYPE)
        provider.query(
            groupsSyncUri(), arrayOf(Groups._ID),
            where, whereArgs, null
        )?.use { cursor ->
            return cursor.count
        }
        throw LocalStorageException("Couldn't count groups")
    }

    /**
     * Enqueues an update of group rows in this address book to the given batch.
     *
     * This method operates "as sync adapter" on [addressBookAccount] and doesn't take the [readOnly] flag into account.
     *
     * @param values    values to update
     * @param where     optional selection
     * @param whereArgs optional arguments for [where]
     * @param batch     batch operation to enqueue the update into
     */
    fun updateGroups(values: ContentValues, where: String? = null, whereArgs: Array<String>? = null, batch: ContactsBatchOperation) {
        // account is implicitly restricted via the URI (asSyncAdapter appends ACCOUNT_NAME/ACCOUNT_TYPE)
        val builder = BatchOperation.CpoBuilder
            .newUpdate(groupsSyncUri())
            .withValues(values)
        if (where != null)
            builder.withSelection(where, whereArgs ?: emptyArray())
        batch += builder
    }

    /**
     * Enqueues a deletion of group rows in this address book to the given batch.
     *
     * This method operates "as sync adapter" on [addressBookAccount] and doesn't take the [readOnly] flag into account.
     *
     * @param where     optional selection
     * @param whereArgs optional arguments for [where]
     * @param batch     batch operation to enqueue the deletion into
     */
    fun deleteGroups(where: String? = null, whereArgs: Array<String>? = null, batch: ContactsBatchOperation) {
        // account is implicitly restricted via the URI (asSyncAdapter appends ACCOUNT_NAME/ACCOUNT_TYPE)
        val builder = BatchOperation.CpoBuilder
            .newDelete(groupsSyncUri())
        if (where != null)
            builder.withSelection(where, whereArgs ?: emptyArray())
        batch += builder
    }

    suspend fun deleteGroupsWithoutMembers() {
        queryGroups(null, null).filter { it.getMembers().isEmpty() }.collect { group ->
            logger.fine("Deleting empty group $group")
            group.delete()
        }
    }

    // endregion

    // region high-res photo access

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
     * Works regardless of the [readOnly] flag: any existing photo data row is deleted before
     * writing, so the provider's internal async processing always inserts a fresh row rather
     * than updating the blocked one.
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
            // Delete any existing photo data row first so that PipeMonitor uses the
            // insert path (mDataId=0) rather than the update path. PipeMonitor's
            // internal update call uses a bare Data URI (no asSyncAdapter), which
            // is silently blocked when IS_READ_ONLY=1. Inserts are not blocked.
            provider.delete(
                ContactsContract.Data.CONTENT_URI.asSyncAdapter(addressBookAccount),
                "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(rawContactId.toString(), Photo.CONTENT_ITEM_TYPE)
            )
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

    // endregion

    // region legacy AndroidContact/AndroidGroup CRUD

    @VisibleForTesting
    internal suspend fun queryGroups(where: String?, whereArgs: Array<String>?): Flow<AndroidGroup> =
        queryGroupRows(null, where, whereArgs).map { AndroidGroup(this, it) }

    @TestOnly
    @Throws(FileNotFoundException::class)
    fun findContactById(id: Long) =
        getRawContactRowOrNull("${RawContacts._ID}=?", arrayOf(id.toString()))
            ?.let { AndroidContact(this, it) }
            ?: throw FileNotFoundException()

    // endregion

    // region helpers

    fun rawContactSyncUri(id: Long) = ContentUris.withAppendedId(RawContacts.CONTENT_URI, id).asSyncAdapter(addressBookAccount)
    fun rawContactsSyncUri() = RawContacts.CONTENT_URI.asSyncAdapter(addressBookAccount)
    fun groupsSyncUri() = Groups.CONTENT_URI.asSyncAdapter(addressBookAccount)

    // endregion


    companion object {

        /**
         * Indicates whether the address book is currently set to read-only (i.e. its contacts and groups have the read-only flag).
         *
         * User data of the address book account (Boolean).
         */
        const val USER_DATA_READ_ONLY = "read_only"

    }

}
