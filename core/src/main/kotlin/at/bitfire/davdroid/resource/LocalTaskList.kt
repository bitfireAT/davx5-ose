/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.DmfsTask
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import org.dmfs.tasks.contract.TaskContract.TaskListColumns
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.logging.Level
import java.util.logging.Logger

/**
 * App-specific implementation of a task list.
 *
 * [TaskLists._SYNC_ID] corresponds to the database collection ID ([at.bitfire.davdroid.db.Collection.id]).
 */
class LocalTaskList (
    val dmfsTaskList: DmfsTaskList
): LocalCollection<LocalTask> {

    private val logger = Logger.getGlobal()

    override val readOnly
        get() = dmfsTaskList.accessLevel?.let {
            it != TaskListColumns.ACCESS_LEVEL_UNDEFINED && it <= TaskListColumns.ACCESS_LEVEL_READ
        } ?: false

    override val dbCollectionId: Long?
        get() = dmfsTaskList.syncId?.toLongOrNull()

    override val tag: String
        get() = "tasks-${dmfsTaskList.account.name}-${dmfsTaskList.id}"

    override val title: String
        get() = dmfsTaskList.name ?: dmfsTaskList.id.toString()

    override var lastSyncState: SyncState?
        get() = dmfsTaskList.readSyncState()?.let { SyncState.fromString(it) }
        set(state) {
            dmfsTaskList.writeSyncState(state.toString())
        }

    override fun findDeleted() = dmfsTaskList.findTasks(Tasks._DELETED, null)
        .map { LocalTask(it) }

    override fun findDirty(): List<LocalTask> {
        val dmfsTasks = dmfsTaskList.findTasks(Tasks._DIRTY, null)
        for (localTask in dmfsTasks) {
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
        return dmfsTasks.map { LocalTask(it) }
    }

    override fun findByName(name: String) =
        dmfsTaskList.findTasks("${Tasks._SYNC_ID}=?", arrayOf(name))
            .firstOrNull()?.let {
                LocalTask(it)
            }


    override fun markNotDirty(flags: Int): Int =
        dmfsTaskList.updateTasks(
            contentValuesOf(DmfsTask.COLUMN_FLAGS to flags),
            "${Tasks.LIST_ID}=? AND ${Tasks._DIRTY}=0",
            arrayOf(dmfsTaskList.id.toString())
        )

    override fun removeNotDirtyMarked(flags: Int) =
        dmfsTaskList.deleteTasks(
            "${Tasks.LIST_ID}=? AND NOT ${Tasks._DIRTY} AND ${DmfsTask.COLUMN_FLAGS}=?",
            arrayOf(dmfsTaskList.id.toString(), flags.toString())
        )

    override fun forgetETags() {
        dmfsTaskList.updateTasks(
            contentValuesOf(DmfsTask.COLUMN_ETAG to null),
            "${Tasks.LIST_ID}=?",
                arrayOf(dmfsTaskList.id.toString())
        )
    }

}