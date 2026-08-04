/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.davtasks

import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.containsNotNull
import at.bitfire.tasks.contract.Tasks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import java.util.logging.Logger

/**
 * Adds support for [DavTaskAndExceptions] data objects to [DavTaskList]. Structurally mirrors
 * [at.bitfire.synctools.storage.tasks.DmfsRecurringTaskList] (§5 of the design doc).
 *
 * v1 note: [at.bitfire.synctools.mapping.davtasks.DavTaskBuilder] never actually produces
 * exceptions yet (RECURRENCE-ID override support is Phase 3, #2357) — but the plumbing to store
 * them once it does is in place here, exactly mirroring the DMFS backend's [Tasks.ORIGINAL_INSTANCE_ID]
 * linkage.
 */
class DavRecurringTaskList(
    val taskList: DavTaskList
) {

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    fun addTaskAndExceptions(taskAndExceptions: DavTaskAndExceptions): Long {
        try {
            val cleaned = cleanUp(taskAndExceptions, mainId = null)

            val batch = DavTasksBatchOperation(taskList.client)
            val idxMainTask = taskList.addTask(cleaned.main, batch)

            for (exception in cleaned.exceptions)
                taskList.addTask(exception, batch, idxOriginalInstanceId = idxMainTask)

            batch.commit()

            val uri = batch.getResult(idxMainTask)?.uri ?: throw LocalStorageException("Content provider returned null on insert")
            return ContentUris.parseId(uri)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't insert task/exceptions", e)
        }
    }

    suspend fun findTaskAndExceptions(where: String?, whereArgs: Array<String>?): DavTaskAndExceptions? =
        queryTasksAndExceptions(where, whereArgs).firstOrNull()

    suspend fun getById(mainTaskId: Long): DavTaskAndExceptions? =
        findTaskAndExceptions("${Tasks._ID}=?", arrayOf(mainTaskId.toString()))

    fun queryTasksAndExceptions(where: String?, whereArgs: Array<String>?): Flow<DavTaskAndExceptions> {
        val (mainWhere, mainWhereArgs) = whereWithMainTasksOnly(where, whereArgs)
        return taskList
            .queryTasks(mainWhere, mainWhereArgs)
            .map { main ->
                val mainTaskId = main.entityValues.getAsLong(Tasks._ID)
                DavTaskAndExceptions(main = main, exceptions = findExceptions(mainTaskId))
            }
    }

    fun updateTaskAndExceptions(id: Long, taskAndExceptions: DavTaskAndExceptions) {
        try {
            val cleaned = cleanUp(taskAndExceptions, mainId = id)

            val batch = DavTasksBatchOperation(taskList.client)
            batch += CpoBuilder.newDelete(taskList.tasksUri())
                .withSelection("${Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(id.toString()))

            taskList.updateTask(id, cleaned.main, batch)

            for (exception in cleaned.exceptions)
                taskList.addTask(exception, batch)

            batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update task/exceptions", e)
        }
    }

    fun deleteTaskAndExceptions(id: Long) {
        try {
            val batch = DavTasksBatchOperation(taskList.client)

            batch += CpoBuilder.newDelete(taskList.taskUri(id))

            batch += CpoBuilder
                .newDelete(taskList.tasksUri())
                .withSelection("${Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(id.toString()))

            batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete task $id", e)
        }
    }


    // validation / cleanup logic

    @VisibleForTesting
    internal fun cleanUp(original: DavTaskAndExceptions, mainId: Long?): DavTaskAndExceptions {
        val main = cleanMainTask(original.main)

        val mainValues = main.entityValues
        val recurring = mainValues.containsNotNull(Tasks.RRULE) || mainValues.containsNotNull(Tasks.RDATE)

        if (!recurring) {
            if (original.exceptions.isNotEmpty())
                logger.warning("Dropping exceptions of task because task is not recurring: $main")
            return DavTaskAndExceptions(main = main, exceptions = emptyList())
        }

        return DavTaskAndExceptions(
            main = main,
            exceptions = original.exceptions.map { originalException ->
                cleanException(originalException, mainId = mainId)
            }
        )
    }

    @VisibleForTesting
    internal fun cleanMainTask(original: Entity): Entity {
        val values = ContentValues(original.entityValues)

        val originalFields = arrayOf(
            Tasks.ORIGINAL_INSTANCE_ID, Tasks.ORIGINAL_INSTANCE_TIME, Tasks.ORIGINAL_INSTANCE_ALLDAY
        )
        for (field in originalFields)
            values.remove(field)

        val result = Entity(values)
        for (subValue in original.subValues)
            result.addSubValue(subValue.uri, subValue.values)
        return result
    }

    @VisibleForTesting
    internal fun cleanException(original: Entity, mainId: Long?): Entity {
        val values = ContentValues(original.entityValues)

        val recurrenceFields = arrayOf(Tasks.RRULE, Tasks.RDATE, Tasks.EXDATE)
        for (field in recurrenceFields)
            values.remove(field)

        if (mainId != null)
            values.put(Tasks.ORIGINAL_INSTANCE_ID, mainId)
        else
            values.remove(Tasks.ORIGINAL_INSTANCE_ID)

        val result = Entity(values)
        for (subValue in original.subValues)
            result.addSubValue(subValue.uri, subValue.values)
        return result
    }


    // helpers for dirty/deleted exceptions

    fun processDeletedExceptions() {
        val batch = DavTasksBatchOperation(taskList.client)

        taskList.iterateTaskRows(
            arrayOf(Tasks._ID, Tasks.ORIGINAL_INSTANCE_ID),
            "${Tasks._DELETED} AND ${Tasks.ORIGINAL_INSTANCE_ID} IS NOT NULL", null
        ) { values ->
            val exceptionId = values.getAsLong(Tasks._ID)
            val mainId = values.getAsLong(Tasks.ORIGINAL_INSTANCE_ID)
            logger.fine("Found deleted exception $exceptionId, removing it and marking original task $mainId as dirty")

            val mainValues = taskList.getTaskRow(mainId, arrayOf(Tasks.SEQUENCE))
            val mainSeq = mainValues?.getAsInteger(Tasks.SEQUENCE) ?: 0

            taskList.updateTaskRow(
                mainId, contentValuesOf(
                    Tasks.SEQUENCE to mainSeq + 1,
                    Tasks._DIRTY to 1
                ), batch
            )

            taskList.deleteTask(exceptionId, batch)
        }

        batch.commit()
    }

    fun processDirtyExceptions() {
        val batch = DavTasksBatchOperation(taskList.client)

        taskList.iterateTaskRows(
            arrayOf(Tasks._ID, Tasks.ORIGINAL_INSTANCE_ID, Tasks.SEQUENCE),
            "${Tasks._DIRTY} AND NOT ${Tasks._DELETED} AND ${Tasks.ORIGINAL_INSTANCE_ID} IS NOT NULL", null
        ) { values ->
            val exceptionId = values.getAsLong(Tasks._ID)
            val mainId = values.getAsLong(Tasks.ORIGINAL_INSTANCE_ID)
            val exceptionSeq = values.getAsInteger(Tasks.SEQUENCE) ?: 0
            logger.fine("Found dirty exception $exceptionId, increasing SEQUENCE and marking main task $mainId as dirty")

            taskList.updateTaskRow(mainId, contentValuesOf(Tasks._DIRTY to 1), batch)

            taskList.updateTaskRow(
                exceptionId, contentValuesOf(
                    Tasks.SEQUENCE to exceptionSeq + 1,
                    Tasks._DIRTY to 0
                ), batch
            )
        }

        batch.commit()
    }


    // helper methods

    private suspend fun findExceptions(mainTaskId: Long): List<Entity> =
        taskList.queryTasks("${Tasks.ORIGINAL_INSTANCE_ID}=?", arrayOf(mainTaskId.toString())).toList()

    private fun whereWithMainTasksOnly(where: String?, whereArgs: Array<String>?): Pair<String, Array<String>> {
        val protectedWhere = "(${where ?: "1"}) AND ${Tasks.ORIGINAL_INSTANCE_ID} IS NULL"
        val protectedWhereArgs = whereArgs ?: arrayOf()
        return Pair(protectedWhere, protectedWhereArgs)
    }

}
