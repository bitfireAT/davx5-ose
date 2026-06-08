/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.resource.LocalTaskList.Companion.COLUMN_TASKLIST_SYNC_STATE
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.DmfsTasksContract
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.TaskListColumns
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.logging.Logger

/**
 * App-specific implementation of a task list.
 *
 * [TaskLists._SYNC_ID] corresponds to the database collection ID ([at.bitfire.davdroid.db.Collection.id]).
 */
class LocalTaskList (
    val dmfsTaskList: DmfsTaskList
): LocalCollection<LocalTask> {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override val readOnly
        get() = dmfsTaskList.accessLevel.let {
            it != TaskListColumns.ACCESS_LEVEL_UNDEFINED && it <= TaskListColumns.ACCESS_LEVEL_READ
        }

    override val dbCollectionId: Long?
        get() = dmfsTaskList.syncId?.toLongOrNull()

    override val tag: String
        get() = "tasks-${dmfsTaskList.account.name}-${dmfsTaskList.id}"

    override val title: String
        get() = dmfsTaskList.name ?: dmfsTaskList.id.toString()

    /** The task list's [SyncState] is stored in serialized JSON format in the [COLUMN_TASKLIST_SYNC_STATE] column. */
    override var lastSyncState: SyncState?
        get() {
            val serializedState = dmfsTaskList.provider
                .getTaskListRow(dmfsTaskList.id, arrayOf(COLUMN_TASKLIST_SYNC_STATE))
                ?.getAsString(COLUMN_TASKLIST_SYNC_STATE)
            return serializedState?.let {
                SyncState.fromString(it)
            }
        }
        set(state) {
            val serializedState = state?.toString()     // don't call "null".toString()
            dmfsTaskList.update(
                contentValuesOf(
                    COLUMN_TASKLIST_SYNC_STATE to serializedState
                )
            )
        }


    override fun countAll(): Int =
        dmfsTaskList.countTasks(null, null)

    override fun countDeleted(): Int =
        dmfsTaskList.countTasks(Tasks._DELETED, null)

    override fun countModified(): Int =
        dmfsTaskList.countTasks("${Tasks._DIRTY} AND NOT ${Tasks._DELETED}", null)

    override fun findDeleted() = dmfsTaskList.findDmfsTasks(Tasks._DELETED, null)
        .map { LocalTask(it) }

    override fun findDirty(): List<LocalTask> =
        dmfsTaskList.findDmfsTasks(Tasks._DIRTY, null).map {
            LocalTask(it)
        }

    override fun findByName(name: String) =
        dmfsTaskList.findDmfsTasks("${Tasks._SYNC_ID}=?", arrayOf(name))
            .firstOrNull()?.let {
                LocalTask(it)
            }


    override fun markNotDirty(flags: Int): Int =
        dmfsTaskList.updateTasks(
            contentValuesOf(DmfsTasksContract.COLUMN_FLAGS to flags),
            "${Tasks.LIST_ID}=? AND ${Tasks._DIRTY}=0",
            arrayOf(dmfsTaskList.id.toString())
        )

    override fun removeNotDirtyMarked(flags: Int) =
        dmfsTaskList.deleteTasks(
            "${Tasks.LIST_ID}=? AND NOT ${Tasks._DIRTY} AND ${DmfsTasksContract.COLUMN_FLAGS}=?",
            arrayOf(dmfsTaskList.id.toString(), flags.toString())
        )

    override fun forgetETags() {
        dmfsTaskList.updateTasks(
            contentValuesOf(DmfsTasksContract.COLUMN_ETAG to null),
            "${Tasks.LIST_ID}=?",
            arrayOf(dmfsTaskList.id.toString())
        )
    }


    companion object {
        /**
         * Column to store per task list sync state as a [String].
         */
        private const val COLUMN_TASKLIST_SYNC_STATE = TaskContract.TaskLists.SYNC_VERSION
    }

}