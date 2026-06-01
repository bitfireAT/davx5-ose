/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.containsNotNull
import org.dmfs.tasks.contract.TaskContract
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Adds support for [TaskAndExceptions] data objects to [DmfsTaskList].
 *
 * This class provides functionality similar to [at.bitfire.synctools.storage.calendar.AndroidRecurringCalendar]
 * but for tasks instead of events. It handles the insertion, updating, and deletion of recurring tasks
 * and their associated exceptions.
 *
 * There are basically two methods for inserting an exception task:
 *
 * 1. Insert it using the tasks provider's exception handling mechanism.
 * 2. Insert it directly as normal task (using [TaskContract.Tasks.CONTENT_URI]). In this case
 * [TaskContract.Tasks.ORIGINAL_INSTANCE_SYNC_ID] must be set to the [TaskContract.Tasks._SYNC_ID] of the
 * original task so that the tasks provider can associate the exception with the main task.
 *
 * This class only uses the second method because it needs to support all sync fields.
 */
class DmfsRecurringTaskList(
    val taskList: DmfsTaskList
) {

    val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Inserts a task and all its exceptions. Input data is first cleaned up using [cleanUp].
     *
     * If you want to insert exceptions, [TaskContract.Tasks._SYNC_ID] must be set on the main
     * task and [TaskContract.Tasks.ORIGINAL_INSTANCE_SYNC_ID] should be set to the same value for the
     * exception tasks. The exception rows must also identify the overridden instance via
     * [TaskContract.Tasks.ORIGINAL_INSTANCE_TIME] and [TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY].
     * **It's not enough to just set [TaskContract.Tasks.ORIGINAL_INSTANCE_ID] in the exceptions**.
     *
     * @param taskAndExceptions    task and exceptions to insert
     *
     * @return ID of the resulting main task
     */
    fun addTaskAndExceptions(taskAndExceptions: TaskAndExceptions): Long {
        try {
            // validate / clean up input
            val cleaned = cleanUp(taskAndExceptions)

            // add main task
            val batch = TasksBatchOperation(taskList.client)
            taskList.addTask(cleaned.main, batch)

            // add exceptions
            for (exception in cleaned.exceptions)
                taskList.addTask(exception, batch)

            batch.commit()

            // main task was created as first row (index 0), return its insert result (= ID)
            val uri = batch.getResult(0)?.uri ?: throw LocalStorageException("Content provider returned null on insert")
            return ContentUris.parseId(uri)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't insert task/exceptions", e)
        }
    }

    /**
     * Find first task (including exceptions) that matches the query from the content provider.
     *
     * Note that the exceptions may contain deleted tasks.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     */
    fun findTaskAndExceptions(where: String?, whereArgs: Array<String>?): TaskAndExceptions? {
        val main = taskList.findTask(where, whereArgs) ?: return null

        // attach exceptions
        val mainTaskId = main.entityValues.getAsLong(TaskContract.Tasks._ID)
        return TaskAndExceptions(
            main = main,
            exceptions = taskList.findTasks("${TaskContract.Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(mainTaskId.toString()))
        )
    }

    /**
     * Retrieves a task and its exceptions from the content provider (associated by [TaskContract.Tasks.ORIGINAL_INSTANCE_ID]).
     *
     * @param mainTaskId   [TaskContract.Tasks._ID] of the main task
     *
     * @return task and exceptions
     */
    fun getById(mainTaskId: Long): TaskAndExceptions? {
        val mainTask = taskList.getTask(mainTaskId) ?: return null
        return TaskAndExceptions(
            main = mainTask,
            exceptions = taskList.findTasks("${TaskContract.Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(mainTaskId.toString()))
        )
    }

    /**
     * Iterates through tasks together with their exceptions from the content provider.
     *
     * Note that the exceptions may contain deleted tasks.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param body          callback that is called for each task (including exceptions)
     */
    fun iterateTaskAndExceptions(where: String?, whereArgs: Array<String>?, body: (TaskAndExceptions) -> Unit) {
        // iterate through main tasks and attach exceptions
        taskList.iterateTaskRows(null, where, whereArgs) { main ->
            val mainTaskId = main.getAsLong(TaskContract.Tasks._ID) ?: return@iterateTaskRows
            body(
                TaskAndExceptions(
                    main = taskList.getTask(mainTaskId) ?: return@iterateTaskRows,
                    exceptions = taskList.findTasks("${TaskContract.Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(mainTaskId.toString()))
                )
            )
        }
    }

    /**
     * Updates a task and all its exceptions. Input data is first cleaned up using
     * [cleanMainTask] and [cleanException].
     *
     * @param id                    ID of the main task row
     * @param taskAndExceptions    new task (including exceptions)
     *
     * @return main task ID of the updated row (may be different than [id] when the task had to be re-created)
     */
    fun updateTaskAndExceptions(id: Long, taskAndExceptions: TaskAndExceptions): Long {
        try {
            // validate / clean up input
            val cleaned = cleanUp(taskAndExceptions)

            // remove old exceptions (because they may be invalid for the updated task)
            val batch = TasksBatchOperation(taskList.client)
            batch += CpoBuilder.newDelete(taskList.tasksUri())
                .withSelection("${TaskContract.Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(id.toString()))

            // update main task
            taskList.updateTask(id, cleaned.main, batch)

            // add updated exceptions
            for (exception in cleaned.exceptions)
                taskList.addTask(exception, batch)

            batch.commit()

            // For tasks, we don't have the same rebuild logic as calendars (no STATUS field issues)
            // so we can just return the original ID
            return id
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update task/exceptions", e)
        }
    }

    /**
     * Deletes a task and all its potential exceptions.
     *
     * @param id    ID of the task
     */
    fun deleteTaskAndExceptions(id: Long) {
        try {
            val batch = TasksBatchOperation(taskList.client)

            // delete main task
            batch += CpoBuilder.newDelete(taskList.taskUri(id))

            // delete exceptions, too (not automatically done by provider)
            batch += CpoBuilder
                .newDelete(taskList.tasksUri())
                .withSelection("${TaskContract.Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(id.toString()))

            batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete task $id", e)
        }
    }


    // validation / clean-up logic

    /**
     * Prepares a task and exceptions so that it can be inserted into the task provider:
     *
     * - If the main task is not recurring or doesn't have a [TaskContract.Tasks._SYNC_ID], exceptions are ignored.
     * - Cleans up the main task with [cleanMainTask].
     * - Cleans up exceptions with [cleanException].
     *
     * @param original  original task and exceptions
     *
     * @return task and exceptions that can actually be inserted
     */
    @VisibleForTesting
    internal fun cleanUp(original: TaskAndExceptions): TaskAndExceptions {
        val main = cleanMainTask(original.main)

        val mainValues = main.entityValues
        val syncId = mainValues.getAsString(TaskContract.Tasks._SYNC_ID)
        val recurring = mainValues.containsNotNull(TaskContract.Tasks.RRULE) || mainValues.containsNotNull(TaskContract.Tasks.RDATE)

        if (syncId == null || !recurring) {
            // 1. main task doesn't have sync id → exceptions wouldn't be associated to main task by task provider, so ignore them
            // 2. main task not recurring → exceptions are useless, ignore them

            if (original.exceptions.isNotEmpty())
                logger.log(Level.WARNING, "Dropping exceptions of task because task is not recurring or _SYNC_ID is not set", main)

            return TaskAndExceptions(main = main, exceptions = emptyList())
        }

        return TaskAndExceptions(
            main = main,
            exceptions = original.exceptions.map { originalException ->
                cleanException(originalException, syncId)
            }
        )
    }

    /**
     * Prepares a main task for insertion into the task provider by making sure it
     * doesn't have fields that a main task shouldn't have (original_instance_...).
     *
     * @param original  original task to insert
     *
     * @return cleaned task that can actually be inserted
     */
    @VisibleForTesting
    internal fun cleanMainTask(original: Entity): Entity {
        // make a copy (don't modify original entity / values)
        val values = ContentValues(original.entityValues)

        // remove values that a main task shouldn't have
        val originalFields = arrayOf(
            TaskContract.Tasks.ORIGINAL_INSTANCE_ID, TaskContract.Tasks.ORIGINAL_INSTANCE_SYNC_ID,
            TaskContract.Tasks.ORIGINAL_INSTANCE_TIME, TaskContract.Tasks.ORIGINAL_INSTANCE_ALLDAY
        )
        for (field in originalFields)
            values.remove(field)

        // create new result with subvalues
        val result = Entity(values)
        for (subValue in original.subValues)
            result.addSubValue(subValue.uri, subValue.values)
        return result
    }

    /**
     * Prepares an exception for insertion into the task provider:
     *
     * - Removes values that an exception shouldn't have (`RRULE`, `RDATE`, `EXDATE`).
     * - Makes sure that the `ORIGINAL_INSTANCE_SYNC_ID` is set to [syncId].
     *
     * @param original  original exception
     * @param syncId    [TaskContract.Tasks._SYNC_ID] of the main task
     *
     * @return cleaned exception that can actually be inserted
     */
    @VisibleForTesting
    internal fun cleanException(original: Entity, syncId: String): Entity {
        // make a copy (don't modify original entity / values)
        val values = ContentValues(original.entityValues)

        // remove values that an exception shouldn't have
        val recurrenceFields = arrayOf(TaskContract.Tasks.RRULE, TaskContract.Tasks.RDATE, TaskContract.Tasks.EXDATE)
        for (field in recurrenceFields)
            values.remove(field)

        // make sure that ORIGINAL_INSTANCE_SYNC_ID is set so that the exception can be associated to the main task
        values.put(TaskContract.Tasks.ORIGINAL_INSTANCE_SYNC_ID, syncId)

        // create new result with subvalues
        val result = Entity(values)
        for (subValue in original.subValues)
            result.addSubValue(subValue.uri, subValue.values)
        return result
    }


    // helpers for dirty/deleted tasks and exceptions

    /**
     * Iterates through all exceptions in [taskList] that are marked as deleted.
     * For every found exception:
     *
     * - the SEQUENCE field of the main task is increased by one,
     * - the main task is marked as dirty (so that it will be synced),
     * - and then the exception is actually deleted (so that it won't show up anymore during sync).
     */
    fun processDeletedExceptions() {
        val batch = TasksBatchOperation(taskList.client)

        // iterate through deleted exceptions
        taskList.iterateTaskRows(
            arrayOf(TaskContract.Tasks._ID, TaskContract.Tasks.ORIGINAL_INSTANCE_ID),
            "${TaskContract.Tasks._DELETED} AND ${TaskContract.Tasks.ORIGINAL_INSTANCE_ID} IS NOT NULL", null
        ) { values ->
            val exceptionId = values.getAsLong(TaskContract.Tasks._ID)          // can't be null (by definition)
            val mainId = values.getAsLong(TaskContract.Tasks.ORIGINAL_INSTANCE_ID)       // can't be null (by query)
            logger.fine("Found deleted exception #$exceptionId, removing it and marking original task #$mainId as dirty")

            // main task: get current sequence
            val mainValues = taskList.getTaskRow(mainId, arrayOf(TaskContract.Tasks.SYNC_VERSION))
            val mainSeq = mainValues?.getAsInteger(TaskContract.Tasks.SYNC_VERSION) ?: 0

            // increase sequence and mark as dirty
            taskList.updateTaskRow(
                mainId, contentValuesOf(
                    TaskContract.Tasks.SYNC_VERSION to mainSeq + 1,
                    TaskContract.Tasks._DIRTY to 1
                ), batch
            )

            // actually remove deleted exception
            taskList.deleteTask(exceptionId, batch)
        }

        batch.commit()
    }

    /**
     * Iterates through all exceptions in [taskList] that are marked as dirty
     * and not marked as deleted.
     *
     * For every found exception:
     *
     * - the SEQUENCE field of the exception is increased by one,
     * - the exception is marked as not dirty anymore,
     * - but the main task is marked as dirty (so that it will be synced).
     */
    fun processDirtyExceptions() {
        val batch = TasksBatchOperation(taskList.client)

        // iterate through dirty exceptions
        taskList.iterateTaskRows(
            arrayOf(TaskContract.Tasks._ID, TaskContract.Tasks.ORIGINAL_INSTANCE_ID, TaskContract.Tasks.SYNC_VERSION),
            "${TaskContract.Tasks._DIRTY} AND NOT ${TaskContract.Tasks._DELETED} AND ${TaskContract.Tasks.ORIGINAL_INSTANCE_ID} IS NOT NULL", null
        ) { values ->
            val exceptionId = values.getAsLong(TaskContract.Tasks._ID)          // can't be null (by definition)
            val mainId = values.getAsLong(TaskContract.Tasks.ORIGINAL_INSTANCE_ID)       // can't be null (by query)
            val exceptionSeq = values.getAsInteger(TaskContract.Tasks.SYNC_VERSION) ?: 0
            logger.fine("Found dirty exception $exceptionId, increasing SEQUENCE and marking main task $mainId as dirty")

            // mark main task as dirty
            taskList.updateTaskRow(
                mainId, contentValuesOf(
                    TaskContract.Tasks._DIRTY to 1
                ), batch
            )

            // increase exception SEQUENCE and set _DIRTY to 0
            taskList.updateTaskRow(
                exceptionId, contentValuesOf(
                    TaskContract.Tasks.SYNC_VERSION to exceptionSeq + 1,
                    TaskContract.Tasks._DIRTY to 0
                ), batch
            )
        }

        batch.commit()
    }

}
