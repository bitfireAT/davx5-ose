/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.db.SyncState
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.DmfsTaskListFactory
import at.bitfire.ical4android.TaskProvider
import org.dmfs.tasks.contract.TaskContract.TaskListColumns
import org.dmfs.tasks.contract.TaskContract.TaskLists
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.logging.Level
import java.util.logging.Logger

/**
 * App-specific implementation of a task list.
 *
 * [TaskLists._SYNC_ID] is used to store the task list URL.
 */
class LocalTaskList private constructor(
        account: Account,
        provider: ContentProviderClient,
        providerName: TaskProvider.ProviderName,
        id: Long
): DmfsTaskList<LocalTask>(account, provider, providerName, LocalTask.Factory, id), LocalCollection<LocalTask> {

    private val logger = Logger.getGlobal()

    private var accessLevel: Int = TaskListColumns.ACCESS_LEVEL_UNDEFINED
    override val readOnly
        get() =
            accessLevel != TaskListColumns.ACCESS_LEVEL_UNDEFINED &&
            accessLevel <= TaskListColumns.ACCESS_LEVEL_READ

    override val collectionUrl: String?
        get() = syncId

    override val tag: String
        get() = "tasks-${account.name}-$id"

    override val title: String
        get() = name ?: id.toString()

    override var lastSyncState: SyncState?
        get() {
            try {
                provider.query(taskListSyncUri(), arrayOf(TaskLists.SYNC_VERSION),
                        null, null, null)?.use { cursor ->
                    if (cursor.moveToNext())
                        cursor.getString(0)?.let {
                            return SyncState.fromString(it)
                        }
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't read sync state", e)
            }
            return null
        }
        set(state) {
            val values = contentValuesOf(TaskLists.SYNC_VERSION to state?.toString())
            provider.update(taskListSyncUri(), values, null, null)
        }


    override fun populate(values: ContentValues) {
        super.populate(values)
        accessLevel = values.getAsInteger(TaskListColumns.ACCESS_LEVEL)
    }


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
                logger.log(Level.WARNING, "Couldn't check/increase sequence", e)
            }
        }
        return tasks
    }

    override fun findByName(name: String) =
            queryTasks("${Tasks._SYNC_ID}=?", arrayOf(name)).firstOrNull()


    override fun markNotDirty(flags: Int): Int {
        val values = contentValuesOf(LocalTask.COLUMN_FLAGS to flags)
        return provider.update(tasksSyncUri(), values,
                "${Tasks.LIST_ID}=? AND ${Tasks._DIRTY}=0",
                arrayOf(id.toString()))
    }

    override fun removeNotDirtyMarked(flags: Int) =
            provider.delete(tasksSyncUri(),
                    "${Tasks.LIST_ID}=? AND NOT ${Tasks._DIRTY} AND ${LocalTask.COLUMN_FLAGS}=?",
                    arrayOf(id.toString(), flags.toString()))

    override fun forgetETags() {
        val values = contentValuesOf(LocalEvent.COLUMN_ETAG to null)
        provider.update(tasksSyncUri(), values, "${Tasks.LIST_ID}=?",
                arrayOf(id.toString()))
    }


    object Factory: DmfsTaskListFactory<LocalTaskList> {

        override fun newInstance(
            account: Account,
            provider: ContentProviderClient,
            providerName: TaskProvider.ProviderName,
            id: Long
        ) = LocalTaskList(account, provider, providerName, id)

    }

}