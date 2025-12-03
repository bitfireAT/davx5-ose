/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.DmfsTask
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.DmfsTaskListFactory
import at.bitfire.ical4android.TaskProvider
import org.dmfs.tasks.contract.TaskContract.TaskListColumns
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.logging.Level
import java.util.logging.Logger

/**
 * App-specific implementation of a task list.
 *
 * [TaskLists._SYNC_ID] corresponds to the database collection ID ([at.bitfire.davdroid.db.Collection.id]).
 */
class LocalTaskList private constructor(
    account: Account,
    provider: ContentProviderClient,
    providerName: TaskProvider.ProviderName,
    id: Long
): DmfsTaskList<LocalTask>(account, provider, providerName, LocalTask.Factory, id), LocalCollection<LocalTask> {

    private val logger = Logger.getGlobal()

    override val readOnly
        get() = accessLevel?.let {
            it != TaskListColumns.ACCESS_LEVEL_UNDEFINED && it <= TaskListColumns.ACCESS_LEVEL_READ
        } ?: false

    override val dbCollectionId: Long?
        get() = syncId?.toLongOrNull()

    override val tag: String
        get() = "tasks-${account.name}-$id"

    override val title: String
        get() = name ?: id.toString()

    override var lastSyncState: SyncState?
        get() = readSyncState()?.let { SyncState.fromString(it) }
        set(state) {
            writeSyncState(state.toString())
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
        val values = contentValuesOf(DmfsTask.COLUMN_FLAGS to flags)
        return provider.update(tasksSyncUri(), values,
                "${Tasks.LIST_ID}=? AND ${Tasks._DIRTY}=0",
                arrayOf(id.toString()))
    }

    override fun removeNotDirtyMarked(flags: Int) =
            provider.delete(tasksSyncUri(),
                    "${Tasks.LIST_ID}=? AND NOT ${Tasks._DIRTY} AND ${DmfsTask.COLUMN_FLAGS}=?",
                    arrayOf(id.toString(), flags.toString()))

    override fun forgetETags() {
        val values = contentValuesOf(DmfsTask.COLUMN_ETAG to null)
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