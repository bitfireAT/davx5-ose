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
 * Cold [Flow] over a content provider query, one row per emission.
 *
 * Runs on [Dispatchers.IO]. Chained blocking work (e.g. a nested query in `.map { }`) needs its
 * own trailing [kotlinx.coroutines.flow.flowOn] — this one only covers the query itself.
 *
 * @param uri           content URI to query
 * @param projection    columns to return
 * @param where         selection
 * @param whereArgs     arguments for selection
 * @throws LocalStorageException when the content provider returns an error
 */
fun ContentProviderClient.queryFlow(
    uri: Uri,
    projection: Array<String>? = null,
    where: String? = null,
    whereArgs: Array<String>? = null
): Flow<ContentValues> =
    flow {
        try {
            query(uri, projection, where, whereArgs, null)?.use { cursor ->
                while (cursor.moveToNext())
                    emit(cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query $uri", e)
        }
    }.flowOn(Dispatchers.IO)    // buffers by default – but main rows are not big enough to worry

/**
 * Like [queryFlow], but for providers that expose rows via an [EntityIterator] (e.g. raw contacts,
 * calendar events), built from the cursor by [buildIterator].
 *
 * @param uri              content URI to query
 * @param projection       columns to return
 * @param where            selection
 * @param whereArgs        arguments for selection
 * @param buildIterator    builds the [EntityIterator] from the query's cursor
 * @throws LocalStorageException when the content provider returns an error
 */
fun ContentProviderClient.queryEntityFlow(
    uri: Uri,
    projection: Array<String>? = null,
    where: String? = null,
    whereArgs: Array<String>? = null,
    buildIterator: (Cursor) -> EntityIterator
): Flow<Entity> =
    flow {
        try {
            query(uri, projection, where, whereArgs, null)?.use { cursor ->
                for (entity in buildIterator(cursor))
                    emit(entity)
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query $uri", e)
        }
    }.flowOn(Dispatchers.IO)            // buffers by default
        .buffer(capacity = Channel.RENDEZVOUS)   // Entity-s could be big → reduce buffer size to 1
