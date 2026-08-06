/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.resource.LocalDavTaskList.Companion.COLUMN_TASKLIST_SYNC_STATE
import at.bitfire.synctools.storage.davtasks.DavRecurringTaskList
import at.bitfire.synctools.storage.davtasks.DavTaskAndExceptions
import at.bitfire.synctools.storage.davtasks.DavTaskList
import at.bitfire.synctools.storage.davtasks.DavTasksContract
import at.bitfire.tasks.contract.TaskLists
import at.bitfire.tasks.contract.Tasks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App-specific implementation of a task list backed by the DAVx⁵-hosted tasks provider.
 * Structurally mirrors [LocalTaskList] (DMFS backend).
 *
 * [TaskLists._SYNC_ID] corresponds to the database collection ID ([at.bitfire.davdroid.db.Collection.id]).
 */
class LocalDavTaskList(
    internal val davTaskList: DavTaskList
): LocalCollection<LocalDavTask> {

    override val readOnly
        get() = davTaskList.accessLevel <= TaskLists.AccessLevel.READ_ONLY

    override val dbCollectionId: Long?
        get() = davTaskList.syncId?.toLongOrNull()

    override val tag: String
        get() = "davtasks-${davTaskList.account.name}-${davTaskList.id}"

    override val title: String
        get() = davTaskList.name ?: davTaskList.id.toString()

    /** The task list's [SyncState] is stored in serialized JSON format in the [COLUMN_TASKLIST_SYNC_STATE] column. */
    override var lastSyncState: SyncState?
        get() {
            val serializedState = davTaskList.provider
                .getTaskListRow(davTaskList.id, arrayOf(COLUMN_TASKLIST_SYNC_STATE))
                ?.getAsString(COLUMN_TASKLIST_SYNC_STATE)
            return serializedState?.let { SyncState.fromString(it) }
        }
        set(state) {
            val serializedState = state?.toString()     // don't call "null".toString()
            davTaskList.update(contentValuesOf(COLUMN_TASKLIST_SYNC_STATE to serializedState))
        }

    internal val recurringTaskList = DavRecurringTaskList(davTaskList)


    fun add(taskAndExceptions: DavTaskAndExceptions): Long =
        recurringTaskList.addTaskAndExceptions(taskAndExceptions)


    // implement LocalCollection

    override fun countAll(): Int =
        davTaskList.countTasks(null, null)

    override fun countDeleted(): Int =
        davTaskList.countTasks(Tasks._DELETED, null)

    override fun countModified(): Int =
        davTaskList.countTasks("${Tasks._DIRTY} AND NOT ${Tasks._DELETED}", null)

    override fun countDirty(): Int =
        davTaskList.countTasks(Tasks._DIRTY, null)

    override fun findDeleted(): Flow<LocalDavTask> =
        recurringTaskList.queryTasksAndExceptions(Tasks._DELETED, null).map { LocalDavTask(recurringTaskList, it) }

    override fun findDirty(): Flow<LocalDavTask> =
        recurringTaskList.queryTasksAndExceptions(Tasks._DIRTY, null).map { LocalDavTask(recurringTaskList, it) }

    override suspend fun findByName(name: String): LocalDavTask? =
        recurringTaskList.findTaskAndExceptions("${Tasks._SYNC_ID}=?", arrayOf(name))
            ?.let { LocalDavTask(recurringTaskList, it) }

    override fun markNotDirty(flags: Int): Int =
        davTaskList.updateTasks(
            contentValuesOf(DavTasksContract.COLUMN_FLAGS to flags),
            "${Tasks.LIST_ID}=? AND ${Tasks._DIRTY}=0",
            arrayOf(davTaskList.id.toString())
        )

    override suspend fun removeNotDirtyMarked(flags: Int) =
        davTaskList.deleteTasks(
            "${Tasks.LIST_ID}=? AND NOT ${Tasks._DIRTY} AND ${DavTasksContract.COLUMN_FLAGS}=?",
            arrayOf(davTaskList.id.toString(), flags.toString())
        )

    override fun forgetETags() {
        davTaskList.updateTasks(
            contentValuesOf(DavTasksContract.COLUMN_ETAG to null),
            "${Tasks.LIST_ID}=?",
            arrayOf(davTaskList.id.toString())
        )
    }


    companion object {
        /** Column to store per task list sync state as a [String]. */
        private const val COLUMN_TASKLIST_SYNC_STATE = TaskLists.SYNC_VERSION
    }

}
