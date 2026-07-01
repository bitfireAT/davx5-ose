/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage

import android.content.ContentProviderClient
import android.content.Entity
import android.content.EntityIterator
import android.database.Cursor
import android.net.Uri
import android.os.RemoteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Cold [Flow] over a content provider query, one row per emission.
 *
 * Runs on [Dispatchers.IO]. Chained blocking work (e.g. a nested query in `.map { }`) needs its
 * own trailing [kotlinx.coroutines.flow.flowOn] — this one only covers the query itself.
 *
 * @param uri           content URI to query
 * @param projection    columns to return
 * @param where         selection
 * @param whereArgs     arguments for selection
 * @param transformRow  maps a cursor row (positioned by the query) to the emitted value
 * @throws LocalStorageException when the content provider returns an error
 */
fun <T> ContentProviderClient.queryFlow(
    uri: Uri,
    projection: Array<String>? = null,
    where: String? = null,
    whereArgs: Array<String>? = null,
    transformRow: (Cursor) -> T
): Flow<T> =
    flow {
        try {
            // Flow cancellation causes the next emit to throw a CancellationException, so the cursor is still closed.
            query(uri, projection, where, whereArgs, null)?.use { cursor ->
                while (cursor.moveToNext())
                    emit(transformRow(cursor))
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query $uri", e)
        }
    }.flowOn(Dispatchers.IO)

/**
 * Like [queryFlow], but for providers that expose rows via an [EntityIterator] (e.g. raw contacts,
 * calendar events), built from the cursor by [newIterator].
 *
 * @param uri              content URI to query
 * @param projection       columns to return
 * @param where            selection
 * @param whereArgs        arguments for selection
 * @param newIterator      builds the [EntityIterator] from the query's cursor
 * @param transformEntity  maps an entity produced by [newIterator] to the emitted value
 * @throws LocalStorageException when the content provider returns an error
 */
fun <T> ContentProviderClient.queryEntityFlow(
    uri: Uri,
    projection: Array<String>? = null,
    where: String? = null,
    whereArgs: Array<String>? = null,
    newIterator: (Cursor) -> EntityIterator,
    transformEntity: (Entity) -> T
): Flow<T> =
    flow {
        try {
            // Flow cancellation causes the next emit to throw a CancellationException, so the cursor is still closed.
            query(uri, projection, where, whereArgs, null)?.use { cursor ->
                for (entity in newIterator(cursor))
                    emit(transformEntity(entity))
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query $uri", e)
        }
    }.flowOn(Dispatchers.IO)
