/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.davtasks

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.os.RemoteException
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.davtasks.DavTasksContract.asSyncAdapter
import at.bitfire.synctools.storage.toContentValues
import at.bitfire.tasks.contract.TaskLists
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Manages locally stored task lists (represented by [DavTaskList]) in the DAVx⁵-hosted tasks
 * provider. Structurally mirrors [at.bitfire.synctools.storage.tasks.DmfsTaskListProvider] (§5 of
 * the design doc) — the difference is that there is only one implementation of this provider (no
 * `ProviderName` variants, no minimum-version check: it's part of the same app).
 *
 * @param account   account that all operations are bound to
 * @param client    content provider client for the tasks provider authority
 * @param authority authority of the tasks provider (discovered at runtime, see [at.bitfire.tasks.contract.TasksContract.discoverAuthority])
 */
class DavTaskListProvider(
    val account: Account,
    internal val client: ContentProviderClient,
    val authority: String
) {

    private val logger
        get() = Logger.getLogger(DavTaskListProvider::class.java.name)


    fun createTaskList(values: ContentValues): Long {
        logger.log(Level.FINE, "Creating local task list with {0}", values)

        values.put(TaskLists.ACCOUNT_NAME, account.name)
        values.put(TaskLists.ACCOUNT_TYPE, account.type)

        val uri = try {
            client.insert(taskListsUri, values)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't create task list", e)
        }
        if (uri == null)
            throw LocalStorageException("Couldn't create task list (empty result from provider)")
        return ContentUris.parseId(uri)
    }

    fun createAndGetTaskList(values: ContentValues): DavTaskList {
        val id = createTaskList(values)
        return getTaskList(id)
            ?: throw LocalStorageException("Couldn't query task list that was just created")
    }

    fun findTaskLists(
        where: String? = null,
        whereArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<DavTaskList> {
        val result = LinkedList<DavTaskList>()
        try {
            client.query(taskListsUri, null, where, whereArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext())
                    result += DavTaskList(this, cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query task lists", e)
        }
        return result
    }

    fun findFirstTaskList(where: String?, whereArgs: Array<String>?, sortOrder: String? = null): DavTaskList? {
        try {
            client.query(taskListsUri, null, where, whereArgs, sortOrder)?.use { cursor ->
                if (cursor.moveToNext())
                    return DavTaskList(this, cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query task lists", e)
        }
        return null
    }

    fun getTaskListRow(id: Long, projection: Array<String>? = null): ContentValues? {
        try {
            client.query(taskListUri(id), projection, null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.toContentValues()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query task list", e)
        }
        return null
    }

    fun getTaskList(id: Long): DavTaskList? =
        getTaskListRow(id)?.let { values -> DavTaskList(this, values) }

    fun updateTaskList(id: Long, info: ContentValues): Int {
        logger.log(Level.FINE, "Updating task list {0} with {1}", arrayOf(id, info))
        try {
            return client.update(taskListUri(id), info, null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update task list", e)
        }
    }

    fun deleteTaskList(id: Long): Boolean {
        logger.log(Level.FINE, "Deleting task list {0}", id)
        try {
            return client.delete(taskListUri(id), null, null) > 0
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete task list", e)
        }
    }


    // helpers

    internal val taskListsUri
        get() = TaskLists.contentUri(authority).asSyncAdapter(account)

    internal fun taskListUri(id: Long) =
        ContentUris.withAppendedId(taskListsUri, id)

}
