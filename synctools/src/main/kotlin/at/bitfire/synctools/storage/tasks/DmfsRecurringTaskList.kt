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
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Adds support for [TaskAndExceptions] data objects to [DmfsTaskList].
 *
 * It handles the insertion, updating, and deletion of recurring tasks and their associated exceptions.
 *
 * Note: OpenTasks supports two methods of linking a recurring task exception to the main task:
 *
 * - set [Tasks.ORIGINAL_INSTANCE_ID] to the main task's [Tasks._ID],
 * - set [Tasks.ORIGINAL_INSTANCE_SYNC_ID] to the main task's [Tasks._SYNC_ID].
 *
 * This class only uses the direct linkage over [Tasks._ID] / [Tasks.ORIGINAL_INSTANCE_ID].
 */
class DmfsRecurringTaskList(
    val taskList: DmfsTaskList
) {

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Inserts a task and all its exceptions. Input data is first cleaned up using [cleanUp].
     *
     * @param taskAndExceptions    task and exceptions to insert
     *
     * @return ID of the resulting main task
     */
    fun addTaskAndExceptions(taskAndExceptions: TaskAndExceptions): Long {
        try {
            // validate / clean up input
            val cleaned = cleanUp(taskAndExceptions, mainId = null)

            // add main task
            val batch = TasksBatchOperation(taskList.client)
            val idxMainTask = taskList.addTask(cleaned.main, batch)

            // add exceptions
            for (exception in cleaned.exceptions)
                taskList.addTask(exception, batch, idxOriginalInstanceId = idxMainTask)

            batch.commit()

            // main task was created as first row, return its insert result (= ID)
            val uri = batch.getResult(idxMainTask)?.uri ?: throw LocalStorageException("Content provider returned null on insert")
            return ContentUris.parseId(uri)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't insert task/exceptions", e)
        }
    }

    /**
     * Find first main task of [taskList] that matches the query from the content provider and attach its exceptions.
     *
     * Note that the exceptions may contain deleted tasks.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     */
    fun findTaskAndExceptions(where: String?, whereArgs: Array<String>?): TaskAndExceptions? {
        val (mainWhere, mainWhereArgs) = whereWithMainTasksOnly(where, whereArgs)
        val main = taskList.findTask(mainWhere, mainWhereArgs) ?: return null

        // attach exceptions
        val mainTaskId = main.entityValues.getAsLong(Tasks._ID)
        return TaskAndExceptions(
            main = main,
            exceptions = findExceptions(mainTaskId)
        )
    }

    /**
     * Retrieves a main task and its exceptions from the content provider.
     *
     * @param mainTaskId   [TaskContract.Tasks._ID] of the main task
     *
     * @return the task and its exceptions, or _null_ if no task with the given id was found
     */
    fun getById(mainTaskId: Long): TaskAndExceptions? =
        findTaskAndExceptions("${Tasks._ID}=?", arrayOf(mainTaskId.toString()))

    /**
     * Iterates through main tasks in [taskList] together with their exceptions.
     *
     * Note that the exceptions may contain deleted tasks.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param body          callback that is called for each task (including exceptions)
     */
    fun iterateTaskAndExceptions(where: String?, whereArgs: Array<String>?, body: (TaskAndExceptions) -> Unit) {
        val (mainWhere, mainWhereArgs) = whereWithMainTasksOnly(where, whereArgs)
        taskList.iterateTasks(mainWhere, mainWhereArgs) { main ->
            val mainTaskId = main.entityValues.getAsLong(Tasks._ID)
            body(
                TaskAndExceptions(
                    main = main,
                    exceptions = findExceptions(mainTaskId)
                )
            )
        }
    }

    /**
     * Updates a task and all its exceptions. Input data is first cleaned up using
     * [cleanMainTask] and [cleanException].
     *
     * @param id                   ID of the main task row
     * @param taskAndExceptions    new task (including exceptions)
     */
    fun updateTaskAndExceptions(id: Long, taskAndExceptions: TaskAndExceptions) {
        try {
            // validate / clean up input
            val cleaned = cleanUp(taskAndExceptions, mainId = id)

            // remove old exceptions (because they may be invalid for the updated task)
            val batch = TasksBatchOperation(taskList.client)
            batch += CpoBuilder.newDelete(taskList.tasksUri())
                .withSelection("${Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(id.toString()))

            // update main task
            taskList.updateTask(id, cleaned.main, batch)

            // add updated exceptions
            for (exception in cleaned.exceptions)
                taskList.addTask(exception, batch)

            batch.commit()
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
                .withSelection("${Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(id.toString()))

            batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete task $id", e)
        }
    }


    // validation / cleanup logic

    /**
     * Prepares a task and exceptions so that it can be inserted into the task provider:
     *
     * - If the main task is not recurring, exceptions are dropped.
     * - Cleans up the main task with [cleanMainTask].
     * - Cleans up exceptions with [cleanException].
     *
     * @param original      original task and exceptions
     * @param mainId        [Tasks._ID] of the main task, if it already has one
     *
     * @return task and exceptions that can actually be inserted
     */
    @VisibleForTesting
    internal fun cleanUp(original: TaskAndExceptions, mainId: Long?): TaskAndExceptions {
        val main = cleanMainTask(original.main)

        val mainValues = main.entityValues
        val recurring = mainValues.containsNotNull(Tasks.RRULE) || mainValues.containsNotNull(Tasks.RDATE)

        if (!recurring) {
            if (original.exceptions.isNotEmpty())
                logger.log(Level.WARNING, "Dropping exceptions of task because task is not recurring", main)
            return TaskAndExceptions(main = main, exceptions = emptyList())
        }

        return TaskAndExceptions(
            main = main,
            exceptions = original.exceptions.map { originalException ->
                cleanException(originalException, mainId = mainId)
            }
        )
    }

    /**
     * Prepares a main task for insertion into the task provider by making sure it
     * doesn't have fields that a main task shouldn't have.
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
            Tasks.ORIGINAL_INSTANCE_ID, Tasks.ORIGINAL_INSTANCE_SYNC_ID,
            Tasks.ORIGINAL_INSTANCE_TIME, Tasks.ORIGINAL_INSTANCE_ALLDAY
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
     * - If [mainId] is provided, sets [Tasks.ORIGINAL_INSTANCE_ID] to it.
     * - Always removes [Tasks.ORIGINAL_INSTANCE_SYNC_ID].
     *
     * @param original  original exception
     * @param mainId    [Tasks._ID] of the main task, or null if not yet assigned
     *
     * @return cleaned exception that can actually be inserted
     */
    @VisibleForTesting
    internal fun cleanException(original: Entity, mainId: Long?): Entity {
        // make a copy (don't modify original entity / values)
        val values = ContentValues(original.entityValues)

        // remove values that an exception shouldn't have
        val recurrenceFields = arrayOf(Tasks.RRULE, Tasks.RDATE, Tasks.EXDATE)
        for (field in recurrenceFields)
            values.remove(field)

        if (mainId != null)
            values.put(Tasks.ORIGINAL_INSTANCE_ID, mainId)
        else
            values.remove(Tasks.ORIGINAL_INSTANCE_ID)
        values.remove(Tasks.ORIGINAL_INSTANCE_SYNC_ID)

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
            arrayOf(Tasks._ID, Tasks.ORIGINAL_INSTANCE_ID),
            "${Tasks._DELETED} AND ${Tasks.ORIGINAL_INSTANCE_ID} IS NOT NULL", null
        ) { values ->
            val exceptionId = values.getAsLong(Tasks._ID)               // can't be null (by definition)
            val mainId = values.getAsLong(Tasks.ORIGINAL_INSTANCE_ID)   // can't be null (by query)
            logger.fine("Found deleted exception $exceptionId, removing it and marking original task $mainId as dirty")

            // main task: get current sequence
            val mainValues = taskList.getTaskRow(mainId, arrayOf(Tasks.SYNC_VERSION))
            val mainSeq = mainValues?.getAsInteger(Tasks.SYNC_VERSION) ?: 0

            // increase sequence and mark as dirty
            taskList.updateTaskRow(
                mainId, contentValuesOf(
                    Tasks.SYNC_VERSION to mainSeq + 1,
                    Tasks._DIRTY to 1
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
            arrayOf(Tasks._ID, Tasks.ORIGINAL_INSTANCE_ID, Tasks.SYNC_VERSION),
            "${Tasks._DIRTY} AND NOT ${Tasks._DELETED} AND ${Tasks.ORIGINAL_INSTANCE_ID} IS NOT NULL", null
        ) { values ->
            val exceptionId = values.getAsLong(Tasks._ID)          // can't be null (by definition)
            val mainId = values.getAsLong(Tasks.ORIGINAL_INSTANCE_ID)       // can't be null (by query)
            val exceptionSeq = values.getAsInteger(Tasks.SYNC_VERSION) ?: 0
            logger.fine("Found dirty exception $exceptionId, increasing SEQUENCE and marking main task $mainId as dirty")

            // mark main task as dirty
            taskList.updateTaskRow(
                mainId, contentValuesOf(
                    Tasks._DIRTY to 1
                ), batch
            )

            // increase exception SEQUENCE and set _DIRTY to 0
            taskList.updateTaskRow(
                exceptionId, contentValuesOf(
                    Tasks.SYNC_VERSION to exceptionSeq + 1,
                    Tasks._DIRTY to 0
                ), batch
            )
        }

        batch.commit()
    }


    // helper methods

    /**
     * Finds all exceptions for a given main task by its ID.
     *
     * @param mainTaskId   The [Tasks._ID] of the main task
     * @return List of exception entities linked to the main task
     */
    private fun findExceptions(mainTaskId: Long): List<Entity> = buildList {
        taskList.iterateTasks("${Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(mainTaskId.toString())) { add(it) }
    }

    private fun whereWithMainTasksOnly(where: String?, whereArgs: Array<String>?): Pair<String, Array<String>> {
        val protectedWhere = "(${where ?: "1"}) AND ${Tasks.ORIGINAL_INSTANCE_ID} IS NULL"
        val protectedWhereArgs = whereArgs ?: arrayOf()
        return Pair(protectedWhere, protectedWhereArgs)
    }

}
