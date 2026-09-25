/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage

import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Entity
import android.content.EntityIterator
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.DeadObjectException
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import at.bitfire.synctools.util.causedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.lang.AutoCloseable

/**
 * Wraps a [ContentProviderClient] to throw [LocalStorageException] instead of [android.os.RemoteException].
 */
class LocalStorageClient(
    val provider: ContentProviderClient
) : AutoCloseable {
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
     * Cold [Flow] over a content provider query, one row per emission.
     *
     * Runs on [Dispatchers.IO]. Chained blocking work (e.g. a nested query in `.map { }`) needs its
     * own trailing [kotlinx.coroutines.flow.flowOn] — this one only covers the query itself.
     *
     * @param uri content URI to query
     * @param projection columns to return
     * @param where selection
     * @param whereArgs arguments for selection
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun queryFlow(
        uri: Uri,
        projection: Array<String>? = null,
        where: String? = null,
        whereArgs: Array<String>? = null
    ): Flow<ContentValues> {
        return flow {
            runWrappingRemoteException {
                query(uri, projection, where, whereArgs, sortOrder = null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        emit(cursor.toContentValues())
                    }
                }
            }
        }.flowOn(Dispatchers.IO)    // buffers by default – but main rows are not big enough to worry
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

    /**
     * @see ContentProviderClient.openAssetFile
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun openAssetFile(url: Uri, mode: String): AssetFileDescriptor? {
        return runWrappingRemoteException {
            provider.openAssetFile(url, mode)
        }
    }

    /**
     * @see ContentProviderClient.openFile
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun openFile(url: Uri, mode: String): ParcelFileDescriptor? {
        return runWrappingRemoteException {
            provider.openFile(url, mode)
        }
    }

    override fun close() {
        provider.close()
    }
}

/**
 * Wraps [RemoteException] in a [LocalStorageException].
 */
internal inline fun <T> runWrappingRemoteException(block: () -> T): T {
    return try {
        block()
    } catch (e: RemoteException) {
        throwWrappedLocalStorageException(e)
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun throwWrappedLocalStorageException(e: RemoteException): Nothing {
    /* A DeadObjectException anywhere in the cause chain means the content provider process died:
    either because it crashed, or because of this Android 14+ behavior:

    1. Holding a ContentProviderClient doesn't keep the provider process "important" - its priority
       is derived from whatever's currently bound to it, recomputed continuously.
    2. Since we're just a background sync worker (not a foreground service), our own importance is
       low, so the provider's derived importance can drop into the "cached" range even while we're
       still connected to it.
    3. Android's app freezer suspends cached processes to save battery/CPU.
    4. If we then make a synchronous call into it while frozen, Android treats this as a bug on our
       side and kills the frozen process.
    5. That kill is what surfaces here as DeadObjectException.

    See AOSP frameworks/base:
    - OomAdjuster.computeProviderHostOomAdjLSP() - derives a provider's importance (adj) from its client,
      showing why holding a connection doesn't pin its priority.
    - com.android.server.am.CachedAppOptimizer - implements freezer and kill-on-sync-call-while-frozen policy.
    - android.os.BinderProxy - where DeadObjectException is actually thrown once the process is dead.

    See also:
    - https://developer.android.com/about/versions/14/behavior-changes-all#cached-apps
    - https://developer.android.com/develop/background-work/services/bound-services#Additional_Notes
      "Always trap DeadObjectException exceptions, which are thrown when the connection has broken."

    Either way, retrying later should work, so mark as a soft error. */
    if (e.causedBy<DeadObjectException>() != null) {
        throw LocalStorageException("Content provider operation failed", e, softError = true)
    } else {
        throw LocalStorageException("Content provider operation failed", e)
    }
}
