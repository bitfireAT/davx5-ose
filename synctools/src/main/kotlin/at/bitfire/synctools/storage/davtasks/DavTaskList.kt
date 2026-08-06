/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.davtasks

import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import android.os.RemoteException
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.davtasks.DavTasksContract.asSyncAdapter
import at.bitfire.synctools.storage.queryFlow
import at.bitfire.synctools.storage.toContentValues
import at.bitfire.tasks.contract.TaskAlarms
import at.bitfire.tasks.contract.TaskProperties
import at.bitfire.tasks.contract.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.annotations.TestOnly
import java.util.logging.Logger

/**
 * Represents a locally stored task list, containing tasks, in the DAVx⁵-hosted tasks provider.
 * Structurally mirrors [at.bitfire.synctools.storage.tasks.DmfsTaskList] (§5 of the design doc).
 *
 * Like the DMFS implementation, properties are always loaded via a separate query rather than a
 * joined `LOAD_PROPERTIES`-style query (simpler to process; this contract doesn't define such a
 * parameter at all).
 */
class DavTaskList(
    val provider: DavTaskListProvider,
    val values: ContentValues
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    val id: Long = values.getAsLong(at.bitfire.tasks.contract.TaskLists._ID)
        ?: throw IllegalArgumentException("${at.bitfire.tasks.contract.TaskLists._ID} must be set")

    val accessLevel: Int
        get() = values.getAsInteger(at.bitfire.tasks.contract.TaskLists.ACCESS_LEVEL) ?: at.bitfire.tasks.contract.TaskLists.AccessLevel.READ_WRITE

    val name: String?
        get() = values.getAsString(at.bitfire.tasks.contract.TaskLists.LIST_NAME)

    val syncId: String?
        get() = values.getAsString(at.bitfire.tasks.contract.TaskLists._SYNC_ID)


    // CRUD tasks

    fun addTask(entity: Entity): Long {
        try {
            val batch = DavTasksBatchOperation(client)
            val backRefIdx = addTask(entity, batch)
            batch.commit()

            val uri = batch.getResult(backRefIdx)?.uri
                ?: throw LocalStorageException("Content provider returned null on insert")
            return ContentUris.parseId(uri)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't insert task", e)
        }
    }

    fun addTask(entity: Entity, batch: DavTasksBatchOperation, idxOriginalInstanceId: Int? = null): Int {
        val taskRowIdx = batch.nextBackrefIdx()

        batch += BatchOperation.CpoBuilder
            .newInsert(tasksUri())
            .withValues(entity.entityValues)
            .apply {
                if (idxOriginalInstanceId != null)
                    withValueBackReference(Tasks.ORIGINAL_INSTANCE_ID, idxOriginalInstanceId)
            }

        for (row in entity.subValues) {
            val (insertUri, taskIdColumn) = subRowTarget(row.uri)
            batch += BatchOperation.CpoBuilder
                .newInsert(insertUri)
                .withValues(row.values)
                .withValueBackReference(taskIdColumn, taskRowIdx)
        }

        return taskRowIdx
    }

    /**
     * Determines the real (sync-adapter) write URI and the `task_id`-equivalent back-reference
     * column for a sub-value, based on the discriminator URI it was tagged with (see [getTask]).
     *
     * Unlike the DMFS backend (one sub-row table: Properties), this contract has two
     * (`Properties` and `Alarms`, §3.3/§3.4 of the design doc), so sub-values must be routed by
     * their tag URI rather than always going to the same table.
     */
    private fun subRowTarget(taggedUri: Uri): Pair<Uri, String> =
        if (taggedUri == tasksAlarmsUri(asSyncAdapter = false))
            tasksAlarmsUri() to TaskAlarms.TASK_ID
        else
            tasksPropertiesUri() to TaskProperties.TASK_ID

    fun countTasks(where: String? = null, whereArgs: Array<String>? = null): Int {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
            client.query(
                tasksUri(), arrayOf(Tasks._ID),
                protectedWhere, protectedWhereArgs, null
            )?.use { cursor -> return cursor.count }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't count tasks", e)
        }
        return 0
    }

    @TestOnly
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

    fun getTask(id: Long): Entity? {
        try {
            client.query(taskUri(id), null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val entity = Entity(cursor.toContentValues())
                    client.query(
                        tasksPropertiesUri(),
                        null,
                        "${TaskProperties.TASK_ID}=?",
                        arrayOf(id.toString()),
                        null
                    )?.use { propertiesCursor ->
                        while (propertiesCursor.moveToNext()) {
                            entity.addSubValue(
                                tasksPropertiesUri(asSyncAdapter = false),
                                propertiesCursor.toContentValues()
                            )
                        }
                    }
                    client.query(
                        tasksAlarmsUri(),
                        null,
                        "${TaskAlarms.TASK_ID}=?",
                        arrayOf(id.toString()),
                        null
                    )?.use { alarmsCursor ->
                        while (alarmsCursor.moveToNext()) {
                            entity.addSubValue(
                                tasksAlarmsUri(asSyncAdapter = false),
                                alarmsCursor.toContentValues()
                            )
                        }
                    }
                    return entity
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query task entity", e)
        }
        return null
    }

    fun getTaskRow(
        id: Long,
        projection: Array<String>? = null,
        where: String? = null,
        whereArgs: Array<String>? = null
    ): ContentValues? {
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

    fun iterateTaskRows(
        projection: Array<String>?,
        where: String?,
        whereArgs: Array<String>?,
        body: (ContentValues) -> Unit
    ) {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
            client.query(tasksUri(), projection, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                while (cursor.moveToNext())
                    body(cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't iterate task rows", e)
        }
    }

    fun queryTasks(where: String?, whereArgs: Array<String>?): Flow<Entity> {
        val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
        return client
            .queryFlow(tasksUri(), null, protectedWhere, protectedWhereArgs)
            .mapNotNull { mainRow ->
                val id = mainRow.getAsLong(Tasks._ID) ?: return@mapNotNull null
                getTask(id)
            }
            .flowOn(Dispatchers.IO)
            .buffer(Channel.RENDEZVOUS)
    }

    fun updateTaskRow(id: Long, values: ContentValues) {
        try {
            client.update(taskUri(id), values, null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update task row $id", e)
        }
    }

    fun updateTaskRow(id: Long, values: ContentValues, batch: DavTasksBatchOperation) {
        batch += BatchOperation.CpoBuilder.newUpdate(taskUri(id)).withValues(values)
    }

    fun updateTask(id: Long, entity: Entity) {
        try {
            val batch = DavTasksBatchOperation(client)
            updateTask(id, entity, batch)
            batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update task $id", e)
        }
    }

    fun updateTasks(values: ContentValues, where: String?, whereArgs: Array<String>?): Int =
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
            client.update(tasksUri(), values, protectedWhere, protectedWhereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update tasks", e)
        }

    fun updateTask(id: Long, entity: Entity, batch: DavTasksBatchOperation) {
        batch += BatchOperation.CpoBuilder
            .newDelete(tasksPropertiesUri())
            .withSelection("${TaskProperties.TASK_ID}=?", arrayOf(id.toString()))
        batch += BatchOperation.CpoBuilder
            .newDelete(tasksAlarmsUri())
            .withSelection("${TaskAlarms.TASK_ID}=?", arrayOf(id.toString()))

        val newValues = ContentValues(entity.entityValues).apply {
            remove(Tasks._ID)
        }
        batch += BatchOperation.CpoBuilder
            .newUpdate(taskUri(id))
            .withValues(newValues)

        for (row in entity.subValues) {
            val (insertUri, taskIdColumn) = subRowTarget(row.uri)
            batch += BatchOperation.CpoBuilder
                .newInsert(insertUri)
                .withValues(ContentValues(row.values).apply {
                    remove(TaskProperties._ID)
                    put(taskIdColumn, id)
                })
        }
    }

    fun deleteTask(id: Long): Int =
        try {
            client.delete(taskUri(id), null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete task $id", e)
        }

    fun deleteTask(id: Long, batch: DavTasksBatchOperation) {
        batch += BatchOperation.CpoBuilder.newDelete(taskUri(id))
    }

    fun deleteTasks(where: String?, whereArgs: Array<String>?): Int =
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
            client.delete(tasksUri(), protectedWhere, protectedWhereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete tasks", e)
        }


    // shortcuts to upper level

    fun delete(): Boolean = provider.deleteTaskList(id)

    fun update(values: ContentValues): Int = provider.updateTaskList(id, values)


    // helpers

    val account
        get() = provider.account

    val client
        get() = provider.client

    internal fun tasksUri(): Uri =
        Tasks.contentUri(provider.authority).asSyncAdapter(account)

    internal fun taskUri(id: Long): Uri =
        ContentUris.withAppendedId(tasksUri(), id)

    /**
     * URI to access the tasks provider's [TaskProperties].
     *
     * **Important: Always do write operations [asSyncAdapter]**, otherwise results will be marked
     * as dirty. Doing everything [asSyncAdapter] is a safe default choice.
     */
    internal fun tasksPropertiesUri(asSyncAdapter: Boolean = true): Uri {
        val uri = TaskProperties.contentUri(provider.authority)
        return if (asSyncAdapter) uri.asSyncAdapter(account) else uri
    }

    /**
     * URI to access the tasks provider's [TaskAlarms] (own table, not a [TaskProperties]
     * mimetype — see §3.4 of the design doc).
     *
     * **Important: Always do write operations [asSyncAdapter]**, otherwise results will be marked
     * as dirty. Doing everything [asSyncAdapter] is a safe default choice.
     */
    internal fun tasksAlarmsUri(asSyncAdapter: Boolean = true): Uri {
        val uri = TaskAlarms.contentUri(provider.authority)
        return if (asSyncAdapter) uri.asSyncAdapter(account) else uri
    }

    private fun whereWithTaskListId(where: String?, whereArgs: Array<String>?): Pair<String, Array<String>> {
        val protectedWhere = "(${where ?: "1"}) AND ${Tasks.LIST_ID}=?"
        val protectedWhereArgs = (whereArgs ?: arrayOf()) + id.toString()
        return Pair(protectedWhere, protectedWhereArgs)
    }

}
