/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.resource.LocalTaskList.Companion.COLUMN_TASKLIST_SYNC_STATE
import at.bitfire.synctools.storage.tasks.DmfsRecurringTaskList
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.DmfsTasksContract
import at.bitfire.synctools.storage.tasks.TaskAndExceptions
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.TaskListColumns
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.LinkedList
import java.util.UUID
import java.util.logging.Logger

/**
 * App-specific implementation of a task list.
 *
 * [org.dmfs.tasks.contract.TaskContract.TaskLists._SYNC_ID] corresponds to the database
 * collection ID ([at.bitfire.davdroid.db.Collection.id]).
 */
class LocalTaskList (
    internal val dmfsTaskList: DmfsTaskList
): LocalCollection<LocalTask> {

    private val logger = Logger.getLogger(javaClass.name)

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

    internal val recurringTaskList = DmfsRecurringTaskList(dmfsTaskList)


    fun add(taskAndExceptions: TaskAndExceptions): Long {
        return recurringTaskList.addTaskAndExceptions(taskAndExceptions)
    }


    // implement LocalResource

    override fun countAll(): Int =
        dmfsTaskList.countTasks(null, null)

    override fun countDeleted(): Int =
        dmfsTaskList.countTasks(Tasks._DELETED, null)

    override fun countModified(): Int =
        dmfsTaskList.countTasks("${Tasks._DIRTY} AND NOT ${Tasks._DELETED}", null)

    override fun findDeleted(): List<LocalTask> {
        val deleted = LinkedList<LocalTask>()
        recurringTaskList.iterateTaskAndExceptions(Tasks._DELETED, null) {
            deleted += LocalTask(recurringTaskList, it)
        }
        return deleted
    }

    override fun findDirty(): List<LocalTask> {
        val dirty = LinkedList<LocalTask>()
        recurringTaskList.iterateTaskAndExceptions(Tasks._DIRTY, null) {
            dirty += LocalTask(recurringTaskList, it)
        }
        return dirty
    }

    override fun findByName(name: String): LocalTask? {
        val matches = recurringTaskList.findAllTasksWithSyncId(name)
        if (matches.isEmpty()) return null

        if (matches.size == 1)
            return LocalTask(recurringTaskList, matches.first())

        // There are multiple tasks with the same _SYNC_ID. This happens when a task was created
        // locally with a UID that already exists on the server: one entry is already synced (has
        // an eTag) and another was never successfully uploaded (dirty, no eTag). Reassign the
        // dirty/no-eTag duplicate a fresh UUID so it can be uploaded as a genuinely new resource.
        for (task in matches) {
            val values = task.main.entityValues
            val isDirty = (values.getAsInteger(Tasks._DIRTY) ?: 0) != 0
            val hasNoETag = values.getAsString(DmfsTasksContract.COLUMN_ETAG) == null
            if (isDirty && hasNoETag) {
                val taskId = values.getAsLong(Tasks._ID) ?: continue
                val newSyncId = "${UUID.randomUUID()}.ics"
                logger.warning("Task $taskId has duplicate _SYNC_ID '$name' but no eTag; reassigning to $newSyncId to resolve collision")
                dmfsTaskList.updateTaskRow(taskId, contentValuesOf(Tasks._SYNC_ID to newSyncId))
            }
        }

        // Return the first remaining match with the original name, preferring one with an eTag.
        val remainingMatches = recurringTaskList.findAllTasksWithSyncId(name)
        val synced =
            remainingMatches.firstOrNull { task ->
                val values = task.main.entityValues
                val hasETag = values.getAsString(DmfsTasksContract.COLUMN_ETAG) != null
                hasETag
            } ?: remainingMatches.firstOrNull() ?: return null
        return LocalTask(recurringTaskList, synced)
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
