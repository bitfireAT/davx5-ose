/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import android.os.RemoteException
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.toContentValues
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.LinkedList
import java.util.logging.Logger


/**
 * Represents a locally stored task list, containing [DmfsTask]s (tasks).
 * Communicates with tasks.org-compatible content providers (currently tasks.org and OpenTasks) to store the tasks.
 *
 * Note: When loading tasks with properties, this implementation explicitly loads properties via separate
 * queries rather than using the `LOAD_PROPERTIES` parameter. This is because the left-join result from
 * `LOAD_PROPERTIES` is error-prone to process (interleaved task and property rows).
 */
class DmfsTaskList(
    val provider: DmfsTaskListProvider,
    val values: ContentValues,
    val providerName: TaskProvider.ProviderName
) {

    // task list properties

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /** see [TaskContract.TaskLists._ID] **/
    val id: Long = values.getAsLong(TaskContract.TaskLists._ID)
        ?: throw IllegalArgumentException("${TaskContract.TaskLists._ID} must be set")

    /** see [TaskContract.TaskListColumns.ACCESS_LEVEL] **/
    val accessLevel: Int
        get() = values.getAsInteger(TaskContract.TaskListColumns.ACCESS_LEVEL) ?: 0

    /** see [TaskContract.TaskLists.LIST_NAME] **/
    val name: String?
        get() = values.getAsString(TaskContract.TaskLists.LIST_NAME)

    /** see [TaskContract.TaskLists._SYNC_ID] **/
    val syncId: String?
        get() = values.getAsString(TaskContract.TaskLists._SYNC_ID)


    // CRUD tasks

    /**
     * Inserts a task into the task provider.
     *
     * @param entity    task to insert (with main row values and sub-values)
     *
     * @return ID of the new task
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun addTask(entity: Entity): Long {
        try {
            val batch = TasksBatchOperation(client)
            val backRefIdx = addTask(entity, batch)
            batch.commit()

            val uri = batch.getResult(backRefIdx)?.uri
                ?: throw LocalStorageException("Content provider returned null on insert")
            return ContentUris.parseId(uri)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't insert task", e)
        }
    }

    /**
     * Enqueues an insert operation for a task into a batch.
     *
     * @param entity    task to insert (with main row values and sub-values)
     * @param batch     batch operation in which the insert is enqueued
     * @param idxOriginalInstanceId   when the task is inserted, [Tasks.ORIGINAL_INSTANCE_ID] is set to
     *                                the result of the previous [batch] operation with that index
     *
     * @return back-reference index of the main task row
     */
    fun addTask(entity: Entity, batch: TasksBatchOperation, idxOriginalInstanceId: Int? = null): Int {
        // insert task row
        val taskRowIdx = batch.nextBackrefIdx()

        batch += BatchOperation.CpoBuilder
            .newInsert(tasksUri())
            .withValues(entity.entityValues)
            .apply {
                if (idxOriginalInstanceId != null)
                    withValueBackReference(Tasks.ORIGINAL_INSTANCE_ID, idxOriginalInstanceId)
            }

        // insert property rows (with reference to task row ID)
        for (row in entity.subValues) {
            batch += BatchOperation.CpoBuilder
                .newInsert(tasksPropertiesUri())
                .withValues(row.values)
                .withValueBackReference(TaskContract.Properties.TASK_ID, taskRowIdx)
        }

        return taskRowIdx
    }

    /**
     * Counts the number of tasks in this task list that match the given selection criteria.
     *
     * @param where An optional filter declaring which rows to return.
     * @param whereArgs Optional arguments for [where].
     * @return The number of tasks matching the selection criteria.
     * @throws LocalStorageException when the content provider returns an error
     */
    fun countTasks(where: String? = null, whereArgs: Array<String>? = null): Int {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
            client.query(
                tasksUri(), arrayOf(Tasks._ID),
                protectedWhere, protectedWhereArgs, null
            )?.use { cursor ->
                return cursor.count
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't count ${providerName.authority} tasks", e)
        }
        // If the query was invalid, an exception should have been thrown. So this should never be reached:
        return 0
    }

    /**
     * Gets the first task row in the task list that matches the given query.
     *
     * @param projection    requested fields
     * @param where         selection
     * @param whereArgs     arguments for selection
     *
     * @return first task row that matches the selection, or `null` if none found
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findTaskRow(projection: Array<String>?, where: String?, whereArgs: Array<String>?): ContentValues? {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
            client.query(tasksUri(), projection, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.toContentValues()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query task rows", e)
        }
        return null
    }

    /**
     * Gets the first task from this task list that matches the given query.
     *
     * @param where     selection
     * @param whereArgs arguments for selection
     *
     * @return first task from this task list that matches the selection, or `null` if none found
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findTask(where: String?, whereArgs: Array<String>?): Entity? {
        val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
        try {
            client.query(tasksUri(), null, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(Tasks._ID))
                    return getTask(id)
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query tasks", e)
        }
        return null
    }

    /**
     * Queries all tasks from this task list.
     *
     * Should be used rarely because it has a potentially large memory footprint.
     * Prefer [iterateTaskRows].
     *
     * @return list of task entities
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findTasks(): List<Entity> {
        val entities = LinkedList<Entity>()
        try {
            iterateTaskRows(null, null, null) { row ->
                val id = row.getAsLong(Tasks._ID) ?: return@iterateTaskRows
                val entity = getTask(id) ?: return@iterateTaskRows
                entities += entity
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query tasks", e)
        }
        return entities
    }

    /**
     * Queries tasks from this task list.
     *
     * @param where     selection
     * @param whereArgs arguments for selection
     *
     * @return tasks from this task list which match the selection
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findTasks(where: String?, whereArgs: Array<String>?): List<Entity> {
        val entities = LinkedList<Entity>()
        try {
            iterateTaskRows(null, where, whereArgs) { row ->
                val id = row.getAsLong(Tasks._ID) ?: return@iterateTaskRows
                val entity = getTask(id) ?: return@iterateTaskRows
                entities += entity
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query tasks", e)
        }
        return entities
    }

    /**
     * Gets a specific task, identified by its ID, from this task list.
     *
     * @param id    task ID
     *
     * @return task entity (or `null` if not found)
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun getTask(id: Long): Entity? {
        try {
            // query tasks
            client.query(taskUri(id, loadProperties = false), null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val entity = Entity(cursor.toContentValues())
                    // explicitly load task properties into subrows
                    client.query(
                        tasksPropertiesUri(),
                        null,
                        "${TaskContract.Properties.TASK_ID}=?",
                        arrayOf(id.toString()),
                        null
                    )?.use { propertiesCursor ->
                        while (propertiesCursor.moveToNext())
                            entity.addSubValue(
                                tasksPropertiesUri(asSyncAdapter = false),
                                propertiesCursor.toContentValues()
                            )
                    }
                    return entity
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query task entity", e)
        }
        return null
    }

    /**
     * Gets a specific task row, identified by its ID, from this task list.
     *
     * @param id        task ID
     * @param projection requested fields
     * @param where      additional selection (appended with AND)
     * @param whereArgs  arguments for [where]
     *
     * @return task row (or `null` if not found)
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun getTaskRow(id: Long, projection: Array<String>? = null, where: String? = null, whereArgs: Array<String>? = null): ContentValues? {
        try {
            client.query(taskUri(id), projection, where, whereArgs, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.toContentValues()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query task row", e)
        }
        return null
    }

    /**
     * Iterates task rows from this task list.
     *
     * @param projection    requested fields
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param body          callback that is called for each main row
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun iterateTaskRows(projection: Array<String>?, where: String?, whereArgs: Array<String>?, body: (ContentValues) -> Unit) {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
            client.query(tasksUri(), projection, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val row = cursor.toContentValues()
                    body(row)
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't iterate task rows", e)
        }
    }

    /**
     * Iterates tasks (with properties) from this task list.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param body          callback that is called for each task entity
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun iterateTasks(where: String?, whereArgs: Array<String>?, body: (Entity) -> Unit) {
        try {
            iterateTaskRows(null, where, whereArgs) { row ->
                val id = row.getAsLong(Tasks._ID) ?: return@iterateTaskRows
                val entity = getTask(id) ?: return@iterateTaskRows
                body(entity)
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't iterate tasks", e)
        }
    }

    /**
     * Updates a specific task's main row with the given values. Doesn't influence property rows.
     *
     * @param id        task ID
     * @param values    new values
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateTaskRow(id: Long, values: ContentValues) {
        try {
            client.update(taskUri(id), values, null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update task row $id", e)
        }
    }

    /**
     * Enqueues an update of a task's main row into a batch operation.
     *
     * @param id        task ID
     * @param values    new values
     * @param batch     batch operation in which the update is enqueued
     */
    fun updateTaskRow(id: Long, values: ContentValues, batch: TasksBatchOperation) {
        batch += BatchOperation.CpoBuilder.newUpdate(taskUri(id)).withValues(values)
    }

    /**
     * Updates a specific task's main row and property rows with the values from the given entity.
     *
     * @param id        task ID
     * @param entity    new values of the task (main row and sub-values)
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateTask(id: Long, entity: Entity) {
        try {
            val batch = TasksBatchOperation(client)
            updateTask(id, entity, batch)
            batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update task $id", e)
        }
    }

    /**
     * Updates tasks in this task list.
     *
     * @param values        values to update
     * @param where         selection
     * @param whereArgs     arguments for selection
     *
     * @return number of updated rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateTasks(values: ContentValues, where: String?, whereArgs: Array<String>?): Int =
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
            client.update(tasksUri(), values, protectedWhere, protectedWhereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update ${providerName.authority} tasks", e)
        }

    /**
     * Enqueues an update of a task into a batch operation.
     *
     * @param id        task ID
     * @param entity    new values of the task (main row and sub-values)
     * @param batch     batch operation in which the update is enqueued
     */
    fun updateTask(id: Long, entity: Entity, batch: TasksBatchOperation) {
        // delete existing property rows for this task
        batch += BatchOperation.CpoBuilder
            .newDelete(tasksPropertiesUri())
            .withSelection("${TaskContract.Properties.TASK_ID}=?", arrayOf(id.toString()))

        // update main row
        val newValues = ContentValues(entity.entityValues).apply {
            remove(Tasks._ID) // don't update task ID
        }
        batch += BatchOperation.CpoBuilder
            .newUpdate(taskUri(id))
            .withValues(newValues)

        // insert new property rows (with reference to task ID)
        for (row in entity.subValues) {
            batch += BatchOperation.CpoBuilder
                .newInsert(tasksPropertiesUri())
                .withValues(ContentValues(row.values).apply {
                    remove(TaskContract.Properties.PROPERTY_ID) // don't reuse property IDs
                    put(TaskContract.Properties.TASK_ID, id)
                })
        }
    }

    /**
     * Deletes a task row.
     *
     * The content provider automatically deletes associated property rows.
     *
     * @param id    ID of the task
     *
     * @return number of affected rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun deleteTask(id: Long): Int =
        try {
            client.delete(taskUri(id), null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete task $id", e)
        }

    /**
     * Enqueues a delete operation for a task into a batch.
     *
     * @param id        ID of the task to delete
     * @param batch     batch operation in which the deletion is enqueued
     */
    fun deleteTask(id: Long, batch: TasksBatchOperation) {
        batch += BatchOperation.CpoBuilder.newDelete(taskUri(id))
    }

    /**
     * Deletes tasks in this task list.
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     *
     * @return number of deleted rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun deleteTasks(where: String?, whereArgs: Array<String>?): Int =
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
            client.delete(tasksUri(), protectedWhere, protectedWhereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete ${providerName.authority} tasks", e)
        }


    // CRUD DmfsTask

    /**
     * Queries tasks from this task list. Adds a WHERE clause that restricts the
     * query to [TaskContract.TaskColumns.LIST_ID] = [id].
     *
     * @param where selection
     * @param whereArgs arguments for selection
     *
     * @return tasks from this task list which match the selection
     */
    @Deprecated("Use findTasks() instead")
    fun findDmfsTasks(where: String? = null, whereArgs: Array<String>? = null): List<DmfsTask> {
        val tasks = LinkedList<DmfsTask>()
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
            client.query(tasksUri(), arrayOf(Tasks._ID), protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val taskId = cursor.getLong(0)
                    val entity = getTask(taskId)
                    if (entity != null)
                        tasks += DmfsTask(this, entity)
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query ${providerName.authority} tasks", e)
        }
        return tasks
    }

    /**
     * Gets a task from this task list by given id.
     *
     * @return task from this task list which matches the selection
     */
    @Deprecated("Use getTask() instead")
    fun getDmfsTask(id: Long): DmfsTask? {
        val values = getTask(id) ?: return null
        return DmfsTask(this, values)
    }


    // other operations

    /**
     * When tasks are added or updated, they may refer to related tasks by UID ([TaskContract.Property.Relation.RELATED_UID]).
     * However, those related tasks may not be available (for instance, because they have not been
     * synchronized yet), so that the tasks provider can't establish the actual relation (= set
     * [TaskContract.PropertyColumns.TASK_ID]) in the database.
     *
     * As soon as such a related task is added, OpenTasks updates the [TaskContract.Property.Relation.RELATED_ID],
     * but it does *not* update [TaskContract.TaskColumns.PARENT_ID] of the parent task:
     * https://github.com/dmfs/opentasks/issues/877
     *
     * This method shall be called after all tasks have been synchronized. It touches
     *
     *   - all [TaskContract.Property.Relation] rows
     *   - with [TaskContract.Property.Relation.RELATED_ID] (→ related task is already synchronized)
     *   - of tasks without [TaskContract.TaskColumns.PARENT_ID] (→ only touch relevant rows)
     *
     * so that missing [TaskContract.TaskColumns.PARENT_ID] fields are updated.
     *
     * @return number of touched [TaskContract.Property.Relation] rows
     */
    fun touchRelations(): Int {
        logger.fine("Touching relations to set parent_id")
        try {
            val batch = TasksBatchOperation(client)
            client.query(
                tasksUri(true), null,
                "${Tasks.LIST_ID}=? AND ${Tasks.PARENT_ID} IS NULL AND ${TaskContract.Property.Relation.MIMETYPE}=? AND ${TaskContract.Property.Relation.RELATED_ID} IS NOT NULL",
                arrayOf(id.toString(), TaskContract.Property.Relation.CONTENT_ITEM_TYPE),
                null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val values = cursor.toContentValues()
                    val id = values.getAsLong(TaskContract.Property.Relation.PROPERTY_ID)
                    val propertyContentUri = ContentUris.withAppendedId(tasksPropertiesUri(), id)
                    batch += BatchOperation.CpoBuilder
                        .newUpdate(propertyContentUri)
                        .withValue(
                            TaskContract.Property.Relation.RELATED_ID,
                            values.getAsLong(TaskContract.Property.Relation.RELATED_ID)
                        )
                }
            }
            return batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't touch ${providerName.authority} task relations", e)
        }
    }


    // shortcuts to upper level

    /** Calls [DmfsTaskListProvider.deleteTaskList] for this task list **/
    fun delete(): Boolean = provider.deleteTaskList(id)

    /**
     * Calls [DmfsTaskListProvider.updateTaskList] for this task list.
     *
     * **Attention**: Does not update this object with the new values!
     */
    fun update(values: ContentValues): Int = provider.updateTaskList(id, values)


    // helpers

    val account
        get() = provider.account

    val client
        get() = provider.client

    internal fun tasksUri(loadProperties: Boolean = false): Uri {
        val uri = Tasks.getContentUri(providerName.authority).asSyncAdapter(account)
        return if (loadProperties)
            uri.buildUpon()
                .appendQueryParameter(TaskContract.LOAD_PROPERTIES, "1")
                .build()
        else
            uri
    }

    internal fun taskUri(id: Long, loadProperties: Boolean = false): Uri =
        ContentUris.withAppendedId(tasksUri(loadProperties), id)

    internal fun tasksPropertiesUri(asSyncAdapter: Boolean = false): Uri {
        val uri = TaskContract.Properties.getContentUri(providerName.authority)
        return if (asSyncAdapter)
            uri.asSyncAdapter(account)
        else
            uri
    }

    /**
     * Restricts a given selection/where clause to this task list ID.
     *
     * @param where      selection
     * @param whereArgs  arguments for selection
     * @return           restricted selection and arguments
     */
    private fun whereWithTaskListId(where: String?, whereArgs: Array<String>?): Pair<String, Array<String>> {
        val protectedWhere = "(${where ?: "1"}) AND ${Tasks.LIST_ID}=?"
        val protectedWhereArgs = (whereArgs ?: arrayOf()) + id.toString()
        return Pair(protectedWhere, protectedWhereArgs)
    }

}