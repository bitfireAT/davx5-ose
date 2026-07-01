/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage

import android.content.ContentProviderClient
import android.content.Entity
import android.content.EntityIterator
import android.database.Cursor
import android.net.Uri
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
 * @param sortOrder     sort order
 * @param transformRow  maps a cursor row (positioned by the query) to the emitted value
 */
fun <T> ContentProviderClient.queryFlow(
    uri: Uri,
    projection: Array<String>? = null,
    where: String? = null,
    whereArgs: Array<String>? = null,
    sortOrder: String? = null,
    transformRow: (Cursor) -> T
): Flow<T> = flow {
    query(uri, projection, where, whereArgs, sortOrder)?.use { cursor ->
        while (cursor.moveToNext())
            emit(transformRow(cursor))
    }
}.flowOn(Dispatchers.IO)

/**
 * Like [queryFlow], but for providers that expose rows via an [EntityIterator] (e.g. raw contacts,
 * calendar events), built from the cursor by [newIterator].
 *
 * @param uri           content URI to query
 * @param projection    columns to return
 * @param where         selection
 * @param whereArgs     arguments for selection
 * @param newIterator   builds the [EntityIterator] from the query's cursor
 * @param transformRow  maps an entity produced by [newIterator] to the emitted value
 */
fun <T> ContentProviderClient.queryEntityFlow(
    uri: Uri,
    projection: Array<String>? = null,
    where: String? = null,
    whereArgs: Array<String>? = null,
    newIterator: (Cursor) -> EntityIterator,
    transformRow: (Entity) -> T
): Flow<T> = flow {
    query(uri, projection, where, whereArgs, null)?.use { cursor ->
        for (entity in newIterator(cursor))
            emit(transformRow(entity))
    }
}.flowOn(Dispatchers.IO)
