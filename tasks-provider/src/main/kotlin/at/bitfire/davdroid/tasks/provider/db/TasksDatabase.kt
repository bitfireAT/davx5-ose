/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.tasks.provider.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The tasks provider's database.
 *
 * Deliberately **not** part of `AppDatabase` (`services.db`), which uses
 * `fallbackToDestructiveMigration(dropAllTables = true)` — an acceptable last resort for cached
 * service metadata, catastrophic for user task data (D6). This database has its own migration
 * cadence and no destructive fallback: a failed migration must surface as a crash/bug report, not
 * as silent data loss.
 */
@Database(
    entities = [
        TaskListEntity::class,
        TaskEntity::class,
        TaskPropertyEntity::class,
        TaskAlarmEntity::class,
        AlarmPropertyEntity::class,
        TaskInstanceEntity::class
    ],
    exportSchema = true,
    version = 1
)
abstract class TasksDatabase : RoomDatabase() {

    abstract fun taskListDao(): TaskListDao
    abstract fun taskDao(): TaskDao
    abstract fun taskPropertyDao(): TaskPropertyDao
    abstract fun taskAlarmDao(): TaskAlarmDao
    abstract fun alarmPropertyDao(): AlarmPropertyDao
    abstract fun taskInstanceDao(): TaskInstanceDao

    companion object {
        private const val DB_NAME = "tasks.db"

        @Volatile
        private var instance: TasksDatabase? = null

        fun getInstance(context: Context): TasksDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): TasksDatabase =
            Room.databaseBuilder(context.applicationContext, TasksDatabase::class.java, DB_NAME)
                .enableMultiInstanceInvalidation()
                // no fallbackToDestructiveMigration: see class doc
                .build()
    }

}
