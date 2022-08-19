/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.log.Logger
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

        fun create(account: Account, provider: TaskProvider, info: Collection): Uri {
            val values = valuesFromCollectionInfo(info, true)
            values.put(TaskLists.OWNER, account.name)
            values.put(TaskLists.SYNC_ENABLED, 1)
            values.put(TaskLists.VISIBLE, 1)
            return create(account, provider, values)
        }

        @SuppressLint("Recycle")
        @Throws(Exception::class)
        fun onRenameAccount(context: Context, oldName: String, newName: String) {
            TaskProvider.acquire(context)?.use { provider ->
                val values = ContentValues(1)
                values.put(Tasks.ACCOUNT_NAME, newName)
                provider.client.update(
                        Tasks.getContentUri(provider.name.authority),
                        values,
                        "${Tasks.ACCOUNT_NAME}=?", arrayOf(oldName)
                )
            }
        }

        private fun valuesFromCollectionInfo(info: Collection, withColor: Boolean): ContentValues {
            val values = ContentValues(3)
            values.put(TaskLists._SYNC_ID, info.url.toString())
            values.put(TaskLists.LIST_NAME, if (info.displayName.isNullOrBlank()) DavUtils.lastSegmentOfUrl(info.url) else info.displayName)

            if (withColor)
                values.put(TaskLists.LIST_COLOR, info.color ?: Constants.DAVDROID_GREEN_RGBA)

            return values
        }

    }

    override val tag: String
        get() = "tasks-${account.name}-$id"

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


    fun update(info: Collection, updateColor: Boolean) =
            update(valuesFromCollectionInfo(info, updateColor))


    override fun findDeleted() = queryTasks(Tasks._DELETED, null)

    override fun findDirty(): List<LocalTask> {
        val tasks = queryTasks(Tasks._DIRTY, null)
        for (localTask in tasks) {
            try {
                val task = requireNotNull(localTask.task)
                val sequence = task.sequence
                if (sequence == null)   // sequence has not been assigned yet (i.e. this task was just locally created)
                    task.sequence = 0
                else                    // task was modified, increase sequence
                    task.sequence = sequence + 1
            } catch(e: Exception) {
                Logger.log.log(Level.WARNING, "Couldn't check/increase sequence", e)
            }
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
                    "${Tasks.LIST_ID}=? AND NOT ${Tasks._DIRTY} AND ${LocalTask.COLUMN_FLAGS}=?",
                    arrayOf(id.toString(), flags.toString()))

    override fun forgetETags() {
        val values = ContentValues(1)
        values.putNull(LocalEvent.COLUMN_ETAG)
        provider.client.update(tasksSyncUri(), values, "${Tasks.LIST_ID}=?",
                arrayOf(id.toString()))
    }


    object Factory: AndroidTaskListFactory<LocalTaskList> {

        override fun newInstance(account: Account, provider: TaskProvider, id: Long) =
                LocalTaskList(account, provider, id)

    }

}