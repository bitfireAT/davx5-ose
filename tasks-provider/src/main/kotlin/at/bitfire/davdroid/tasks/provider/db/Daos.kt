/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.tasks.provider.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import at.bitfire.tasks.contract.TaskAlarms
import at.bitfire.tasks.contract.TaskLists
import at.bitfire.tasks.contract.TaskProperties
import at.bitfire.tasks.contract.Tasks

/**
 * Typed, safe access to the tables — used by the sync engine ([at.bitfire.synctools], same
 * process) and by [at.bitfire.davdroid.tasks.provider.DavTasksProvider] itself for operations
 * with no caller-supplied SQL fragments (e.g. dirtying a parent task).
 *
 * The public [android.content.ContentProvider] surface for arbitrary caller-supplied
 * projection/selection strings does *not* go through these DAOs — see
 * `DavTasksProvider.query/insert/update/delete`, which uses `SQLiteQueryBuilder` with
 * `setStrict(true)` instead (§4 of the design doc).
 */
@Dao
interface TaskListDao {
    @Insert
    fun insert(taskList: TaskListEntity): Long

    @Update
    fun update(taskList: TaskListEntity)

    @Query("SELECT * FROM ${TaskLists.PATH} WHERE ${TaskLists._ID} = :id")
    fun get(id: Long): TaskListEntity?

    @Query("SELECT * FROM ${TaskLists.PATH} WHERE ${TaskLists.ACCOUNT_NAME} = :accountName AND ${TaskLists.ACCOUNT_TYPE} = :accountType")
    fun getByAccount(accountName: String, accountType: String): List<TaskListEntity>

    @Query("SELECT * FROM ${TaskLists.PATH} WHERE ${TaskLists._SYNC_ID} = :syncId AND ${TaskLists.ACCOUNT_NAME} = :accountName AND ${TaskLists.ACCOUNT_TYPE} = :accountType")
    fun getBySyncId(syncId: String, accountName: String, accountType: String): TaskListEntity?

    @Delete
    fun delete(taskList: TaskListEntity)
}

@Dao
interface TaskDao {
    @Insert
    fun insert(task: TaskEntity): Long

    @Update
    fun update(task: TaskEntity)

    @Query("SELECT * FROM ${Tasks.PATH} WHERE ${Tasks._ID} = :id")
    fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM ${Tasks.PATH} WHERE ${Tasks.LIST_ID} = :listId AND ${Tasks._DELETED} = 0")
    fun getByList(listId: Long): List<TaskEntity>

    @Query("SELECT * FROM ${Tasks.PATH} WHERE ${Tasks._UID} = :uid AND ${Tasks.LIST_ID} = :listId AND ${Tasks.ORIGINAL_INSTANCE_ID} IS NULL")
    fun getMainByUid(uid: String, listId: Long): TaskEntity?

    /** Marks a task dirty — used to propagate dirtiness from a child [TaskPropertyEntity]/[TaskAlarmEntity] row (§4). */
    @Query("UPDATE ${Tasks.PATH} SET ${Tasks._DIRTY} = 1 WHERE ${Tasks._ID} = :id")
    fun markDirty(id: Long)

    @Delete
    fun delete(task: TaskEntity)
}

@Dao
interface TaskPropertyDao {
    @Insert
    fun insert(property: TaskPropertyEntity): Long

    @Update
    fun update(property: TaskPropertyEntity)

    @Query("SELECT * FROM ${TaskProperties.PATH} WHERE ${TaskProperties.TASK_ID} = :taskId")
    fun getByTask(taskId: Long): List<TaskPropertyEntity>

    @Query("SELECT * FROM ${TaskProperties.PATH} WHERE ${TaskProperties.TASK_ID} = :taskId AND ${TaskProperties.MIMETYPE} = :mimetype")
    fun getByTaskAndMimetype(taskId: Long, mimetype: String): List<TaskPropertyEntity>

    @Query("DELETE FROM ${TaskProperties.PATH} WHERE ${TaskProperties.TASK_ID} = :taskId")
    fun deleteByTask(taskId: Long)

    @Delete
    fun delete(property: TaskPropertyEntity)
}

@Dao
interface TaskAlarmDao {
    @Insert
    fun insert(alarm: TaskAlarmEntity): Long

    @Update
    fun update(alarm: TaskAlarmEntity)

    @Query("SELECT * FROM ${TaskAlarms.PATH} WHERE ${TaskAlarms.TASK_ID} = :taskId")
    fun getByTask(taskId: Long): List<TaskAlarmEntity>

    @Query("DELETE FROM ${TaskAlarms.PATH} WHERE ${TaskAlarms.TASK_ID} = :taskId")
    fun deleteByTask(taskId: Long)

    @Delete
    fun delete(alarm: TaskAlarmEntity)
}

@Dao
interface AlarmPropertyDao {
    @Insert
    fun insert(property: AlarmPropertyEntity): Long

    @Query("SELECT * FROM ${at.bitfire.tasks.contract.AlarmProperties.PATH} WHERE ${at.bitfire.tasks.contract.AlarmProperties.ALARM_ID} = :alarmId")
    fun getByAlarm(alarmId: Long): List<AlarmPropertyEntity>

    @Delete
    fun delete(property: AlarmPropertyEntity)
}

@Dao
interface TaskInstanceDao {
    @Insert
    fun insert(instance: TaskInstanceEntity): Long

    @Query("SELECT * FROM ${at.bitfire.tasks.contract.TaskInstances.PATH} WHERE ${at.bitfire.tasks.contract.TaskInstances.TASK_ID} = :taskId")
    fun getByTask(taskId: Long): List<TaskInstanceEntity>

    @Query("DELETE FROM ${at.bitfire.tasks.contract.TaskInstances.PATH} WHERE ${at.bitfire.tasks.contract.TaskInstances.TASK_ID} = :taskId")
    fun deleteByTask(taskId: Long)
}
