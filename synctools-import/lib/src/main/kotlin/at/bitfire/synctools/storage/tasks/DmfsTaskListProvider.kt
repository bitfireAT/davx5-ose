/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.os.RemoteException
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.toContentValues
import org.dmfs.tasks.contract.TaskContract
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Manages locally stored tasklists (represented by [DmfsTaskList]) in
 * DmfsTask tasks provider.
 *
 * @param account   Account that all operations are bound to
 * @param client    content provider client
 */
class DmfsTaskListProvider(
    val account: Account,
    internal val client: ContentProviderClient,
    val providerName: TaskProvider.ProviderName
) {

    private val logger
        get() = Logger.getLogger(DmfsTaskList::class.java.name)


    // DmfsTaskList CRUD

    fun createTaskList(values: ContentValues): Long {
        logger.log(Level.FINE, "Creating ${providerName.authority} local task list", values)

        values.put(TaskContract.ACCOUNT_NAME, account.name)
        values.put(TaskContract.ACCOUNT_TYPE, account.type)

        val uri = try {
            client.insert(taskListsUri, values)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't create task list", e)
        }
        if (uri == null)
            throw LocalStorageException("Couldn't create task list (empty result from provider)")
        return ContentUris.parseId(uri)
    }

    /**
     * Creates a new task list and directly returns it.
     *
     * @param values    values to create the task list from (account name and type are inserted)
     *
     * @return the created task list
     * @throws LocalStorageException when the content provider returns nothing or an error
     */
    fun createAndGetTaskList(values: ContentValues): DmfsTaskList {
        val id = createTaskList(values)
        return getTaskList(id) ?: throw LocalStorageException("Couldn't query ${providerName.authority} task list that was just created")
    }

    /**
     * Queries existing task lists.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param sortOrder     sort order
     *
     * @return list of task lists
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findTaskLists(where: String? = null, whereArgs: Array<String>? = null, sortOrder: String? = null): List<DmfsTaskList> {
        val result = LinkedList<DmfsTaskList>()
        try {
            client.query(taskListsUri, null, where, whereArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext())
                    result += DmfsTaskList(this, cursor.toContentValues(), providerName)
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query ${providerName.authority} task lists", e)
        }
        return result
    }

    /**
     * Queries existing task lists and returns the first task list that matches the search criteria.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param sortOrder     sort order
     *
     * @return first task list that matches the search criteria (or `null`)
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findFirstTaskList(where: String?, whereArgs: Array<String>?, sortOrder: String? = null): DmfsTaskList? {
        try {
            client.query(taskListsUri, null, where, whereArgs, sortOrder)?.use { cursor ->
                if (cursor.moveToNext())
                    return DmfsTaskList(this, cursor.toContentValues(), providerName)
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query ${providerName.authority} task lists", e)
        }
        return null
    }

    /**
     * Gets an existing task list by its ID.
     *
     * @param id    task list ID
     *
     * @return task list (or `null` if not found)
     * @throws LocalStorageException when the content provider returns an error
     */
    fun getTaskList(id: Long): DmfsTaskList? {
        try {
            client.query(taskListUri(id), null, null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return DmfsTaskList(this, cursor.toContentValues(), providerName)
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query ${providerName.authority} task list", e)
        }
        return null
    }

    fun updateTaskList(id: Long, info: ContentValues): Int {
        logger.log(Level.FINE, "Updating ${providerName.authority} task list (#$id)", info)
        try {
            return client.update(taskListUri(id), info, null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update ${providerName.authority} task list", e)
        }
    }

    /**
     * Deletes this task list from the local task list provider.
     *
     * @return `true` if the task list was deleted, `false` otherwise (like it was not there before the call)
     */
    fun delete(id: Long): Boolean {
        logger.log(Level.FINE, "Deleting ${providerName.authority} task list (#$id)")
        try {
            return client.delete(taskListUri(id), null, null) > 0
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete ${providerName.authority} task list", e)
        }
    }


    // other methods: sync state

    fun readTaskListSyncState(id: Long): String? =
        try {
            client.query(taskListUri(id), arrayOf(COLUMN_TASKLIST_SYNC_STATE), null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.getString(0)
                else
                    null
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query ${providerName.authority} task list sync state", e)
        }

    fun writeTaskListSyncState(id: Long, state: String?) {
        try {
            val values = contentValuesOf(COLUMN_TASKLIST_SYNC_STATE to state)
            client.update(taskListUri(id), values, null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query ${providerName.authority} task list", e)
        }
    }


    // helpers

    val taskListsUri
        get() = TaskContract.TaskLists.getContentUri(providerName.authority).asSyncAdapter(account)

    fun taskListUri(id: Long) =
        ContentUris.withAppendedId(taskListsUri, id)

    companion object {

        /**
         * Column to store per task list sync state as a [String].
         */
        private const val COLUMN_TASKLIST_SYNC_STATE = TaskContract.TaskLists.SYNC_VERSION

    }

}