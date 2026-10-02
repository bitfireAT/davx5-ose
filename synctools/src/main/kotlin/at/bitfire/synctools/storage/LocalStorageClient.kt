/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage

import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Entity
import android.content.EntityIterator
import android.database.Cursor
import android.net.Uri
import android.os.RemoteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

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
     * Like [queryFlow], but for providers that expose rows via an [EntityIterator] (e.g. raw contacts,
     * calendar events), built from the cursor by [buildIterator].
     *
     * @param uri content URI to query
     * @param projection columns to return
     * @param where selection
     * @param whereArgs arguments for selection
     * @param buildIterator builds the [EntityIterator] from the query's cursor
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun queryEntityFlow(
        uri: Uri,
        projection: Array<String>? = null,
        where: String? = null,
        whereArgs: Array<String>? = null,
        buildIterator: (Cursor) -> EntityIterator
    ): Flow<Entity> {
        return flow {
            runWrappingRemoteException {
                query(uri, projection, where, whereArgs, sortOrder = null)?.use { cursor ->
                    for (entity in buildIterator(cursor)) {
                        emit(entity)
                    }
                }
            }
        }.flowOn(Dispatchers.IO)            // buffers by default
            .buffer(capacity = Channel.RENDEZVOUS)   // Entity-s could be big → reduce buffer size to 1
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
