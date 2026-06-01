/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import at.bitfire.ical4android.Task
import at.bitfire.synctools.mapping.tasks.DmfsTaskBuilder
import at.bitfire.synctools.mapping.tasks.DmfsTaskProcessor
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.toContentValues
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.RelatedTo
import org.dmfs.tasks.contract.TaskContract
import java.io.FileNotFoundException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Stores and retrieves tasks to/from the tasks.org-content provider (currently tasks.org and
 * OpenTasks).
 *
 * A task in the context of this class is one row in the [org.dmfs.tasks.contract.TaskContract.Tasks] table,
 * plus associated data rows (like alarms and reminders).
 *
 * The SEQUENCE field is stored in [org.dmfs.tasks.contract.TaskContract.CommonSyncColumns.SYNC_VERSION], so
 * don't use it for anything else.
 *
 * @param taskList  task list where the task is stored
 * @param values    entity with all columns, as returned by the task provider; [org.dmfs.tasks.contract.TaskContract.Tasks._ID]
 *                  must be set to a non-null value for existing tasks, and may be absent for new (unsaved) tasks
 */
class DmfsTask(
    val taskList: DmfsTaskList,
    val values: Entity
) {

    /**
     * Secondary constructor for creating a new (not yet saved) task.
     *
     * @param taskList  task list where the task will be stored
     * @param task      task data
     * @param syncId    remote file name (e.g. `mytask.ics`)
     * @param eTag      remote ETag
     * @param flags     local flags (e.g. [at.bitfire.davdroid.resource.LocalResource.FLAG_REMOTELY_PRESENT])
     */
    constructor(taskList: DmfsTaskList, task: Task, syncId: String?, eTag: String?, flags: Int) : this(
        taskList = taskList,
        values = Entity(ContentValues().apply {
            if (syncId != null) put(TaskContract.Tasks._SYNC_ID, syncId)
            put(COLUMN_ETAG, eTag)
            put(COLUMN_FLAGS, flags)
        })
    ) {
        this.task = task
    }

    private val logger = Logger.getLogger(javaClass.name)

    private val mainValues
        get() = values.entityValues

    var id: Long? = mainValues.getAsLong(TaskContract.Tasks._ID)
    var syncId: String? = mainValues.getAsString(TaskContract.Tasks._SYNC_ID)
    var eTag: String? = mainValues.getAsString(COLUMN_ETAG)
    var flags: Int = mainValues.getAsInteger(COLUMN_FLAGS) ?: 0

    var task: Task? = null
        /**
         * This getter returns the full task data, either from [task] or, if [task] is null, by reading task
         * number [id] from the task provider
         * @throws IllegalArgumentException if task has not been saved yet
         * @throws java.io.FileNotFoundException if there's no task with [id] in the task provider
         * @throws android.os.RemoteException on task provider errors
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

                        if (values.containsKey(TaskContract.Properties.PROPERTY_ID)) {
                            // process the first property, which is combined with the task row
                            processor.populateProperty(values, newTask)

                            while (cursor.moveToNext()) {
                                // process the other properties
                                processor.populateProperty(cursor.toContentValues(), newTask)
                            }
                        }

                        // Special case: parent_id set, but no matching parent Relation row (like given by aCalendar+)
                        val relatedToList = newTask.relatedTo
                        values.getAsLong(TaskContract.Tasks.PARENT_ID)?.let { parentId ->
                            val hasParentRelation = relatedToList.any { relatedTo ->
                                val relatedType = relatedTo.getParameter<RelType>(Parameter.RELTYPE)
                                relatedType == RelType.PARENT || relatedType == null /* RelType.PARENT is the default value */
                            }
                            if (!hasParentRelation) {
                                // get UID of parent task
                                val parentContentUri = ContentUris.withAppendedId(taskList.tasksUri(), parentId)
                                client.query(parentContentUri, arrayOf(TaskContract.Tasks._UID), null, null, null)?.use { cursor ->
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
     * @throws at.bitfire.synctools.storage.LocalStorageException when the tasks provider doesn't return a result row
     * @throws android.os.RemoteException on tasks provider errors
     */
    @Deprecated("Use DmfsTaskList.addTask() instead")
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
     * @throws android.os.RemoteException on tasks provider errors
     */
    @Deprecated("Use DmfsTaskList.updateTask() or DmfsTaskList.updateTaskRow() instead")
    fun update(task: Task): Uri {
        this.task = task
        val existingId = requireNotNull(id)

        val batch = TasksBatchOperation(taskList.provider.client)

        // remove associated rows which are added later again
        batch += BatchOperation.CpoBuilder
            .newDelete(taskList.tasksPropertiesUri())
            .withSelection("${TaskContract.Properties.TASK_ID}=?", arrayOf(existingId.toString()))

        // update task
        val builder = DmfsTaskBuilder(taskList, task, id, syncId, eTag, flags)
        builder.updateRows(batch)

        batch.commit()
        return ContentUris.withAppendedId(TaskContract.Tasks.getContentUri(taskList.providerName.authority), existingId)
    }

    /**
     * Shortcut for [DmfsTaskList.updateTaskRow] with [id].
     */
    fun update(values: ContentValues) {
        taskList.updateTaskRow(id!!, values)
    }

    /**
     * Shortcut for [DmfsTaskList.deleteTask] with [id].
     */
    fun delete(): Int = taskList.deleteTask(id!!)

    private fun taskSyncURI(loadProperties: Boolean = false): Uri {
        val id = requireNotNull(id)
        return ContentUris.withAppendedId(taskList.tasksUri(loadProperties), id)
    }

    companion object {
        const val UNKNOWN_PROPERTY_DATA = TaskContract.Properties.DATA0

        const val COLUMN_ETAG = TaskContract.Tasks.SYNC1

        const val COLUMN_FLAGS = TaskContract.Tasks.SYNC2
    }

}