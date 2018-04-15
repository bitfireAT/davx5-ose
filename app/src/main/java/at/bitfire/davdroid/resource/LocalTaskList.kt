/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.annotation.SuppressLint
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.SyncState
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.AndroidTaskListFactory
import at.bitfire.ical4android.TaskProvider
import org.dmfs.tasks.contract.TaskContract.TaskLists
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.logging.Level

class LocalTaskList private constructor(
        account: Account,
        provider: TaskProvider,
        id: Long
): AndroidTaskList<LocalTask>(account, provider, LocalTask.Factory, id), LocalCollection<LocalTask> {

    companion object {

        fun tasksProviderAvailable(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                return context.packageManager.resolveContentProvider(TaskProvider.ProviderName.OpenTasks.authority, 0) != null
            else
                try {
                    TaskProvider.acquire(context, TaskProvider.ProviderName.OpenTasks)?.use {
                        return true
                    }
                } catch (e: Exception) {
                    // couldn't acquire task provider
                }
                return false
        }

        fun create(account: Account, provider: TaskProvider, info: CollectionInfo): Uri {
            val values = valuesFromCollectionInfo(info, true)
            values.put(TaskLists.OWNER, account.name)
            values.put(TaskLists.SYNC_ENABLED, 1)
            values.put(TaskLists.VISIBLE, 1)
            return create(account, provider, values)
        }

        @SuppressLint("Recycle")
        @Throws(Exception::class)
        fun onRenameAccount(resolver: ContentResolver, oldName: String, newName: String) {
            var client: ContentProviderClient? = null
            try {
                client = resolver.acquireContentProviderClient(TaskProvider.ProviderName.OpenTasks.authority)
                client?.use {
                    val values = ContentValues(1)
                    values.put(Tasks.ACCOUNT_NAME, newName)
                    it.update(Tasks.getContentUri(TaskProvider.ProviderName.OpenTasks.authority), values, "${Tasks.ACCOUNT_NAME}=?", arrayOf(oldName))
                }
            } finally {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 24)
                    client?.close()
                else
                    client?.release()
            }
        }

        private fun valuesFromCollectionInfo(info: CollectionInfo, withColor: Boolean): ContentValues {
            val values = ContentValues(3)
            values.put(TaskLists._SYNC_ID, info.url.toString())
            values.put(TaskLists.LIST_NAME, if (info.displayName.isNullOrBlank()) DavUtils.lastSegmentOfUrl(info.url) else info.displayName)

            if (withColor)
                values.put(TaskLists.LIST_COLOR, info.color ?: Constants.DAVDROID_GREEN_RGBA)

            return values
        }

    }

    override val title: String
        get() = name ?: id.toString()

    override var lastSyncState: SyncState?
        get() {
            try {
                provider.client.query(taskListSyncUri(), arrayOf(TaskLists.SYNC_VERSION),
                        null, null, null)?.use { cursor ->
                    if (cursor.moveToNext())
                        cursor.getString(0)?.let {
                            return SyncState.fromString(it)
                        }
                }
            } catch (e: Exception) {
                Logger.log.log(Level.WARNING, "Couldn't read sync state", e)
            }
            return null
        }
        set(state) {
            val values = ContentValues(1)
            values.put(TaskLists.SYNC_VERSION, state?.toString())
            provider.client.update(taskListSyncUri(), values, null, null)
        }


    fun update(info: CollectionInfo, updateColor: Boolean) =
            update(valuesFromCollectionInfo(info, updateColor))


    override fun findDeleted() = queryTasks("${Tasks._DELETED}!=0", null)

    override fun findDirty(): List<LocalTask> {
        val tasks = queryTasks("${Tasks._DIRTY}!=0", null)
        for (localTask in tasks) {
            val task = requireNotNull(localTask.task)
            val sequence = task.sequence
            if (sequence == null)    // sequence has not been assigned yet (i.e. this task was just locally created)
                task.sequence = 0
            else
                task.sequence = sequence + 1
        }
        return tasks
    }

    override fun findByName(name: String) =
            queryTasks("${Tasks._SYNC_ID}=?", arrayOf(name)).firstOrNull()


    override fun markNotDirty(flags: Int): Int {
        val values = ContentValues(1)
        values.put(LocalTask.COLUMN_FLAGS, flags)
        return provider.client.update(tasksSyncUri(), values,
                "${Tasks.LIST_ID}=? AND ${Tasks._DIRTY}=0",
                arrayOf(id.toString()))
    }

    override fun removeNotDirtyMarked(flags: Int) =
            provider.client.delete(tasksSyncUri(),
                    "${Tasks.LIST_ID}=? AND ${Tasks._DIRTY}=0 AND ${LocalTask.COLUMN_FLAGS}=?",
                    arrayOf(id.toString(), flags.toString()))


    object Factory: AndroidTaskListFactory<LocalTaskList> {

        override fun newInstance(account: Account, provider: TaskProvider, id: Long) =
                LocalTaskList(account, provider, id)

    }

}