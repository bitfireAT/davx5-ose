/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.os.RemoteException
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.toContentValues
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Manages locally stored jtx collections (journals, notes, tasks), each represented by a
 * [JtxCollection], in the jtx Board content provider.
 *
 * @param account   Account that all operations are bound to
 * @param client    content provider client
 */
class JtxCollectionProvider(
    val account: Account,
    internal val client: ContentProviderClient
) {

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)


    // JtxCollection CRUD

    /**
     * Creates a new jtx collection.
     *
     * @param values    values to create the collection from (account name and type are inserted)
     * @return collection ID of the newly created collection
     * @throws LocalStorageException when the content provider returns nothing or an error
     */
    fun createCollection(values: ContentValues): Long {
        logger.log(Level.FINE, "Creating jtx collection", values)

        values.put(JtxContract.JtxCollection.ACCOUNT_NAME, account.name)
        values.put(JtxContract.JtxCollection.ACCOUNT_TYPE, account.type)

        val uri =
            try {
                client.insert(collectionsUri, values)
            } catch (e: RemoteException) {
                throw LocalStorageException("Couldn't create jtx collection", e)
            }
        if (uri == null)
            throw LocalStorageException("Couldn't create jtx collection")
        return ContentUris.parseId(uri)
    }

    /**
     * Creates a new jtx collection and directly returns it.
     *
     * @param values    values to create the collection from (account name and type are inserted)
     * @return the created collection
     * @throws LocalStorageException when the content provider returns nothing or an error
     */
    fun createAndGetCollection(values: ContentValues): JtxCollection {
        val id = createCollection(values)
        return getCollection(id) ?: throw LocalStorageException("Couldn't query jtx collection that was just created")
    }

    /**
     * Queries existing jtx collections.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param sortOrder     sort order
     * @return list of collections
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findCollections(where: String? = null, whereArgs: Array<String>? = null, sortOrder: String? = null): List<JtxCollection> {
        val result = LinkedList<JtxCollection>()
        try {
            client.query(collectionsUri, null, where, whereArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext())
                    result += JtxCollection(this, cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query jtx collections", e)
        }
        return result
    }

    /**
     * Queries existing jtx collections and returns the first one that matches the search criteria.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param sortOrder     sort order
     * @return first collection that matches the search criteria (or `null`)
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findFirstCollection(where: String? = null, whereArgs: Array<String>? = null, sortOrder: String? = null): JtxCollection? {
        try {
            client.query(collectionsUri, null, where, whereArgs, sortOrder)?.use { cursor ->
                if (cursor.moveToNext())
                    return JtxCollection(this, cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query jtx collections", e)
        }
        return null
    }

    /**
     * Gets an existing jtx collection by its ID.
     *
     * @param id    collection ID
     * @return collection (or `null` if not found)
     * @throws LocalStorageException when the content provider returns an error
     */
    fun getCollection(id: Long): JtxCollection? {
        try {
            client.query(collectionUri(id), null, null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return JtxCollection(this, cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query jtx collection", e)
        }
        return null
    }

    /**
     * Updates an existing jtx collection.
     *
     * @param id        collection ID
     * @param values    values to update
     * @return number of updated rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateCollection(id: Long, values: ContentValues): Int {
        logger.log(Level.FINE, "Updating jtx collection #$id", values)
        try {
            return client.update(collectionUri(id), values, null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update jtx collection", e)
        }
    }

    /**
     * Deletes an existing jtx collection.
     *
     * @param id    collection ID
     * @return number of deleted rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun deleteCollection(id: Long): Int {
        logger.fine("Deleting jtx collection #$id")
        try {
            return client.delete(collectionUri(id), null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete jtx collection", e)
        }
    }


    // sync state

    /**
     * Reads the collection sync state ([JtxContract.JtxCollection.SYNC_VERSION] field).
     *
     * @param id    collection ID
     * @return sync state (or `null` if not set)
     * @throws LocalStorageException when the content provider returns an error
     */
    fun readCollectionSyncState(id: Long): String? =
        try {
            client.query(collectionUri(id), arrayOf(JtxContract.JtxCollection.SYNC_VERSION), null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    cursor.getString(0)
                else
                    null
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query jtx collection sync state", e)
        }

    /**
     * Writes the collection sync state ([JtxContract.JtxCollection.SYNC_VERSION] field).
     *
     * @param id    collection ID
     * @param state sync state (may be `null`)
     * @throws LocalStorageException when the content provider returns an error
     */
    fun writeCollectionSyncState(id: Long, state: String?) {
        updateCollection(id, contentValuesOf(JtxContract.JtxCollection.SYNC_VERSION to state))
    }


    // helpers

    val collectionsUri
        get() = JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(account)

    fun collectionUri(id: Long) =
        ContentUris.withAppendedId(collectionsUri, id)

}
