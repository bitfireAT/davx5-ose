/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.RemoteException
import at.bitfire.synctools.mapping.tasks.DmfsTaskBuilder
import at.bitfire.synctools.mapping.tasks.DmfsTaskProcessor
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.TasksBatchOperation
import at.bitfire.synctools.storage.toContentValues
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.RelatedTo
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.io.FileNotFoundException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Stores and retrieves VTODO iCalendar objects (represented as [Task]s) to/from the
 * tasks.org-content provider (currently tasks.org and OpenTasks).
 *
 * Extend this class to process specific fields of the task.
 *
 * The SEQUENCE field is stored in [Tasks.SYNC_VERSION], so don't use [Tasks.SYNC_VERSION]
 * for anything else.
 */
@Deprecated("Use storage.tasks.DmfsTask instead")
class DmfsTask(
    val taskList: DmfsTaskList
) {

    private val logger = Logger.getLogger(javaClass.name)

    var id: Long? = null
    var syncId: String? = null
    var eTag: String? = null
    var flags: Int = 0


    constructor(taskList: DmfsTaskList, values: ContentValues): this(taskList) {
        id = values.getAsLong(Tasks._ID)
        syncId = values.getAsString(Tasks._SYNC_ID)
        eTag = values.getAsString(COLUMN_ETAG)
        flags = values.getAsInteger(COLUMN_FLAGS) ?: 0
    }

    constructor(taskList: DmfsTaskList, task: Task, syncId: String?, eTag: String?, flags: Int): this(taskList) {
        this.task = task
        this.syncId = syncId
        this.eTag = eTag
        this.flags = flags
    }


    var task: Task? = null
        /**
         * This getter returns the full task data, either from [task] or, if [task] is null, by reading task
         * number [id] from the task provider
         * @throws IllegalArgumentException if task has not been saved yet
         * @throws FileNotFoundException if there's no task with [id] in the task provider
         * @throws RemoteException on task provider errors
         */
        get() {
            if (field != null)
                return field
            val id = requireNotNull(id)

            try {
                val client = taskList.provider.client
                client.query(taskSyncURI(true), null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        // create new Task which will be populated
                        val newTask = Task()
                        field = newTask

                        val values = cursor.toContentValues()
                        logger.log(Level.FINER, "Found task", values)
                        val processor = DmfsTaskProcessor(taskList)
                        processor.populateTask(values, newTask)

                        if (values.containsKey(Properties.PROPERTY_ID)) {
                            // process the first property, which is combined with the task row
                            processor.populateProperty(values, newTask)

                            while (cursor.moveToNext()) {
                                // process the other properties
                                processor.populateProperty(cursor.toContentValues(), newTask)
                            }
                        }

                        // Special case: parent_id set, but no matching parent Relation row (like given by aCalendar+)
                        val relatedToList = newTask.relatedTo
                        values.getAsLong(Tasks.PARENT_ID)?.let { parentId ->
                            val hasParentRelation = relatedToList.any { relatedTo ->
                                val relatedType = relatedTo.getParameter<RelType>(Parameter.RELTYPE)
                                relatedType == RelType.PARENT || relatedType == null /* RelType.PARENT is the default value */
                            }
                            if (!hasParentRelation) {
                                // get UID of parent task
                                val parentContentUri = ContentUris.withAppendedId(taskList.tasksUri(), parentId)
                                client.query(parentContentUri, arrayOf(Tasks._UID), null, null, null)?.use { cursor ->
                                    if (cursor.moveToNext()) {
                                        // add RelatedTo for parent task
                                        relatedToList += RelatedTo(cursor.getString(0))
                                    }
                                }
                            }
                        }

                        field = newTask
                        return newTask
                    }
                }
            } catch (e: Exception) {
                /* Populating event has been interrupted by an exception, so we reset the event to
                avoid an inconsistent state. This also ensures that the exception will be thrown
                again on the next get() call. */
                field = null
                throw e
            }
            throw FileNotFoundException("Couldn't find task #$id")
        }

    /**
     * Saves the unsaved [task] into the task provider storage.
     *
     * @return content URI of the created task
     *
     * @throws LocalStorageException when the tasks provider doesn't return a result row
     * @throws RemoteException on tasks provider errors
     */
    fun add(): Uri {
        val batch = TasksBatchOperation(taskList.provider.client)

        val requiredTask = requireNotNull(task)
        val builder = DmfsTaskBuilder(taskList, requiredTask, id, syncId, eTag, flags)
        builder.addRows(batch)

        batch.commit()

        val resultUri = batch.getResult(0)?.uri
            ?: throw LocalStorageException("Empty result from provider when adding a task")
        id = ContentUris.parseId(resultUri)
        return resultUri
    }

    /**
     * Updates an already existing task in the tasks provider storage with the values
     * from the instance.
     *
     * @return content URI of the updated task
     *
     * @throws LocalStorageException when the tasks provider doesn't return a result row
     * @throws RemoteException on tasks provider errors
     */
    fun update(task: Task): Uri {
        this.task = task
        val existingId = requireNotNull(id)

        val batch = TasksBatchOperation(taskList.provider.client)

        // remove associated rows which are added later again
        batch += CpoBuilder
            .newDelete(taskList.tasksPropertiesUri())
            .withSelection("${Properties.TASK_ID}=?", arrayOf(existingId.toString()))

        // update task
        val builder = DmfsTaskBuilder(taskList, task, id, syncId, eTag, flags)
        builder.updateRows(batch)

        batch.commit()
        return ContentUris.withAppendedId(Tasks.getContentUri(taskList.providerName.authority), existingId)
    }

    fun update(values: ContentValues) {
        taskList.provider.client.update(taskSyncURI(), values, null, null)
    }

    /**
     * Deletes an existing task from the tasks provider storage.
     *
     * @return number of affected rows
     *
     * @throws RemoteException on tasks provider errors
     */
    fun delete(): Int {
        return taskList.provider.client.delete(taskSyncURI(), null, null)
    }

    private fun taskSyncURI(loadProperties: Boolean = false): Uri {
        val id = requireNotNull(id)
        return ContentUris.withAppendedId(taskList.tasksUri(loadProperties), id)
    }

    companion object {
        const val UNKNOWN_PROPERTY_DATA = Properties.DATA0

        const val COLUMN_ETAG = Tasks.SYNC1

        const val COLUMN_FLAGS = Tasks.SYNC2
    }

}
