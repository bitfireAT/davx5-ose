/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage

import android.content.ContentProviderClient
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.RemoteException

/**
 * Wraps a [ContentProviderClient] to throw [LocalStorageException] instead of [android.os.RemoteException].
 */
class LocalStorageClient(
    val provider: ContentProviderClient
) {
    /**
     * @see ContentProviderClient.query
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun query(
        url: Uri,
        projection: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): Cursor? {
        return runWrappingRemoteException {
            provider.query(url, projection, selection, selectionArgs, sortOrder)
        }
    }

    /**
     * @see ContentProviderClient.insert
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun insert(
        url: Uri,
        initialValues: ContentValues
    ): Uri? {
        return runWrappingRemoteException {
            provider.insert(url, initialValues)
        }
    }

    /**
     * @see ContentProviderClient.bulkInsert
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun bulkInsert(url: Uri, initialValues: Array<ContentValues>): Int {
        return runWrappingRemoteException {
            provider.bulkInsert(url, initialValues)
        }
    }

    /**
     * @see ContentProviderClient.update
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun update(
        url: Uri,
        values: ContentValues?,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): Int {
        return runWrappingRemoteException {
            provider.update(url, values, selection, selectionArgs)
        }
    }

    /**
     * @see ContentProviderClient.delete
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun delete(
        url: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): Int {
        return runWrappingRemoteException {
            provider.delete(url, selection, selectionArgs)
        }
    }
}

/**
 * Wraps [RemoteException] in a [LocalStorageException].
 */
internal inline fun <T> runWrappingRemoteException(block: () -> T): T {
    return try {
        block()
    } catch (e: RemoteException) {
        throw LocalStorageException("Content provider operation failed", e)
    }
}
