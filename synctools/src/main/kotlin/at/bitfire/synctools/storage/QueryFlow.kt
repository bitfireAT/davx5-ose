/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage

import android.content.ContentProviderClient
import android.content.Entity
import android.content.EntityIterator
import android.database.Cursor
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Runs a content provider query and emits each row as a cold [Flow], transformed by [transform].
 * The query is executed and the cursor is iterated lazily, only when the flow is collected; the
 * cursor is closed once the flow completes or is cancelled. No producer coroutine/thread is used.
 *
 * Doesn't switch dispatchers itself — callers should apply [kotlinx.coroutines.flow.flowOn] as
 * needed, since content provider access is blocking.
 */
fun <T> ContentProviderClient.queryFlow(
    uri: Uri,
    projection: Array<String>? = null,
    where: String? = null,
    whereArgs: Array<String>? = null,
    sortOrder: String? = null,
    transform: (Cursor) -> T
): Flow<T> = flow {
    query(uri, projection, where, whereArgs, sortOrder)?.use { cursor ->
        while (cursor.moveToNext())
            emit(transform(cursor))
    }
}

/**
 * Like [queryFlow], but iterates rows as [Entity] via [newIterator], for content providers that
 * expose their rows through an [EntityIterator] (e.g. raw contacts and calendar events).
 */
fun <T> ContentProviderClient.queryEntityFlow(
    uri: Uri,
    projection: Array<String>? = null,
    where: String? = null,
    whereArgs: Array<String>? = null,
    newIterator: (Cursor) -> EntityIterator,
    transform: (Entity) -> T
): Flow<T> = flow {
    query(uri, projection, where, whereArgs, null)?.use { cursor ->
        for (entity in newIterator(cursor))
            emit(transform(entity))
    }
}
