/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.tasks.provider

import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentUris
import android.content.ContentValues
import android.content.OperationApplicationException
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.os.Build
import androidx.sqlite.db.SimpleSQLiteQuery
import at.bitfire.davdroid.tasks.provider.db.TasksDatabase
import at.bitfire.davdroid.tasks.provider.recurrence.InstanceMaintainer
import at.bitfire.tasks.contract.AlarmProperties
import at.bitfire.tasks.contract.TaskAlarms
import at.bitfire.tasks.contract.TaskInstances
import at.bitfire.tasks.contract.TaskLists
import at.bitfire.tasks.contract.TaskProperties
import at.bitfire.tasks.contract.Tasks
import at.bitfire.tasks.contract.TasksContract

/**
 * DAVx⁵'s VTODO [ContentProvider] (design doc: `doc/tasks-provider.md`, Phase 1 "provider core").
 *
 * Every third-party frontend interacts with this class only through the [android.net.Uri] /
 * [ContentValues] surface defined by `at.bitfire.tasks.contract`. It has no notion of *which*
 * DAVx⁵ build variant hosts it (see [TasksContract.discoverAuthority] and the `<provider>`
 * declaration with `${tasksAuthority}` in the app manifest).
 *
 * Sub-tables are declared with generic `data1..dataN` columns (D4), so [TaskProperties] and
 * [AlarmProperties] don't need per-mimetype code paths here beyond column-name validation.
 *
 * Security-relevant, see §4 of the design doc:
 *  - [query] uses [SQLiteQueryBuilder] with a fixed table and a fixed `projectionMap`, so a
 *    malicious `projection` can only reference that table's own columns.
 *  - [insert]/[update] reject any [ContentValues] key not in the table's known column set —
 *    `ContentValues` keys become raw SQL column identifiers, so this is not optional.
 *  - Selection/sort-order strings are otherwise passed through as-is: this is inherent to the
 *    `ContentProvider` contract (callers are expected to supply SQL fragments), the malicious
 *    case this guards against is a fragment that reaches into another table.
 */
class DavTasksProvider : ContentProvider() {

    companion object {
        private const val TASK_LISTS = 1
        private const val TASK_LIST_ID = 2
        private const val TASKS = 3
        private const val TASK_ID = 4
        private const val PROPERTIES = 5
        private const val PROPERTY_ID = 6
        private const val ALARMS = 7
        private const val ALARM_ID = 8
        private const val ALARM_PROPERTIES = 9
        private const val ALARM_PROPERTY_ID = 10
        private const val INSTANCES = 11
        private const val INSTANCE_ID = 12

        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            // "*" matches any authority: the actual authority is only known at runtime (D1)
            addURI("*", TaskLists.PATH, TASK_LISTS)
            addURI("*", "${TaskLists.PATH}/#", TASK_LIST_ID)
            addURI("*", Tasks.PATH, TASKS)
            addURI("*", "${Tasks.PATH}/#", TASK_ID)
            addURI("*", TaskProperties.PATH, PROPERTIES)
            addURI("*", "${TaskProperties.PATH}/#", PROPERTY_ID)
            addURI("*", TaskAlarms.PATH, ALARMS)
            addURI("*", "${TaskAlarms.PATH}/#", ALARM_ID)
            addURI("*", AlarmProperties.PATH, ALARM_PROPERTIES)
            addURI("*", "${AlarmProperties.PATH}/#", ALARM_PROPERTY_ID)
            addURI("*", TaskInstances.PATH, INSTANCES)
            addURI("*", "${TaskInstances.PATH}/#", INSTANCE_ID)
        }

        private val TASK_LISTS_COLUMNS = setOf(
            TaskLists._ID, TaskLists.ACCOUNT_NAME, TaskLists.ACCOUNT_TYPE, TaskLists._SYNC_ID,
            TaskLists.SYNC_VERSION, TaskLists.SYNC1, TaskLists.SYNC2, TaskLists.SYNC3, TaskLists.SYNC4,
            TaskLists.LIST_NAME, TaskLists.LIST_DESCRIPTION, TaskLists.LIST_COLOR, TaskLists.LIST_OWNER,
            TaskLists.ACCESS_LEVEL, TaskLists.VISIBLE, TaskLists.SYNC_ENABLED, TaskLists.SUPPORTED_COMPONENTS
        )
        private val TASKS_COLUMNS = setOf(
            Tasks._ID, Tasks.LIST_ID, Tasks._UID, Tasks._SYNC_ID, Tasks._DIRTY, Tasks._DELETED,
            Tasks.SYNC_VERSION, Tasks.SYNC1, Tasks.SYNC2, Tasks.SYNC3, Tasks.SYNC4,
            Tasks.ORIGINAL_INSTANCE_ID, Tasks.ORIGINAL_INSTANCE_TIME, Tasks.ORIGINAL_INSTANCE_ALLDAY,
            Tasks.SUMMARY, Tasks.DESCRIPTION, Tasks.LOCATION, Tasks.GEO_LAT, Tasks.GEO_LON, Tasks.URL,
            Tasks.COLOR, Tasks.ORGANIZER, Tasks.ORGANIZER_CN, Tasks.ORGANIZER_SENT_BY, Tasks.PRIORITY,
            Tasks.CLASSIFICATION, Tasks.STATUS, Tasks.PERCENT_COMPLETE, Tasks.COMPLETED, Tasks.COMPLETED_ALLDAY,
            Tasks.SEQUENCE, Tasks.CREATED, Tasks.LAST_MODIFIED, Tasks.DTSTAMP, Tasks.DTSTART, Tasks.DTSTART_TZ,
            Tasks.DUE, Tasks.DUE_TZ, Tasks.DURATION, Tasks.IS_ALLDAY, Tasks.RRULE, Tasks.RDATE, Tasks.EXDATE
        )
        private val PROPERTIES_COLUMNS = setOf(
            TaskProperties._ID, TaskProperties.TASK_ID, TaskProperties.MIMETYPE,
            TaskProperties.DATA1, TaskProperties.DATA2, TaskProperties.DATA3, TaskProperties.DATA4,
            TaskProperties.DATA5, TaskProperties.DATA6, TaskProperties.DATA7, TaskProperties.DATA8,
            TaskProperties.DATA9, TaskProperties.DATA10, TaskProperties.DATA11, TaskProperties.DATA12,
            TaskProperties.DATA13, TaskProperties.DATA14, TaskProperties.DATA15,
            TaskProperties.DATA_SYNC1, TaskProperties.DATA_SYNC2, TaskProperties.DATA_SYNC3, TaskProperties.DATA_SYNC4
        )
        private val ALARMS_COLUMNS = setOf(
            TaskAlarms._ID, TaskAlarms.TASK_ID, TaskAlarms.ACTION, TaskAlarms.TRIGGER_RELATIVE,
            TaskAlarms.TRIGGER_RELATED, TaskAlarms.TRIGGER_ABSOLUTE, TaskAlarms.DURATION, TaskAlarms.REPEAT,
            TaskAlarms.DESCRIPTION, TaskAlarms.SUMMARY, TaskAlarms.UID, TaskAlarms.ACKNOWLEDGED,
            TaskAlarms.RELATED_TO, TaskAlarms.PROXIMITY
        )
        private val ALARM_PROPERTIES_COLUMNS = setOf(
            AlarmProperties._ID, AlarmProperties.ALARM_ID, AlarmProperties.MIMETYPE,
            AlarmProperties.DATA1, AlarmProperties.DATA2, AlarmProperties.DATA3
        )
        private val INSTANCES_COLUMNS = setOf(
            TaskInstances._ID, TaskInstances.TASK_ID, TaskInstances.INSTANCE_START, TaskInstances.INSTANCE_DUE,
            TaskInstances.INSTANCE_START_SORTING, TaskInstances.INSTANCE_DUE_SORTING, TaskInstances.DISTANCE_FROM_CURRENT
        )

        private data class TableSpec(val tableName: String, val idColumn: String, val allowedColumns: Set<String>)

        private val TABLE_SPECS = mapOf(
            TASK_LISTS to TableSpec(TaskLists.PATH, TaskLists._ID, TASK_LISTS_COLUMNS),
            TASK_LIST_ID to TableSpec(TaskLists.PATH, TaskLists._ID, TASK_LISTS_COLUMNS),
            TASKS to TableSpec(Tasks.PATH, Tasks._ID, TASKS_COLUMNS),
            TASK_ID to TableSpec(Tasks.PATH, Tasks._ID, TASKS_COLUMNS),
            PROPERTIES to TableSpec(TaskProperties.PATH, TaskProperties._ID, PROPERTIES_COLUMNS),
            PROPERTY_ID to TableSpec(TaskProperties.PATH, TaskProperties._ID, PROPERTIES_COLUMNS),
            ALARMS to TableSpec(TaskAlarms.PATH, TaskAlarms._ID, ALARMS_COLUMNS),
            ALARM_ID to TableSpec(TaskAlarms.PATH, TaskAlarms._ID, ALARMS_COLUMNS),
            ALARM_PROPERTIES to TableSpec(AlarmProperties.PATH, AlarmProperties._ID, ALARM_PROPERTIES_COLUMNS),
            ALARM_PROPERTY_ID to TableSpec(AlarmProperties.PATH, AlarmProperties._ID, ALARM_PROPERTIES_COLUMNS),
            INSTANCES to TableSpec(TaskInstances.PATH, TaskInstances._ID, INSTANCES_COLUMNS),
            INSTANCE_ID to TableSpec(TaskInstances.PATH, TaskInstances._ID, INSTANCES_COLUMNS)
        )

        private val ITEM_MATCHES = setOf(TASK_LIST_ID, TASK_ID, PROPERTY_ID, ALARM_ID, ALARM_PROPERTY_ID, INSTANCE_ID)
        private val READ_ONLY_MATCHES = setOf(INSTANCES, INSTANCE_ID)
        private val DIR_MATCHES = setOf(TASK_LISTS, TASKS, PROPERTIES, ALARMS, ALARM_PROPERTIES, INSTANCES)
    }

    private lateinit var database: TasksDatabase
    private lateinit var instanceMaintainer: InstanceMaintainer

    override fun onCreate(): Boolean {
        database = TasksDatabase.getInstance(requireNotNull(context))
        instanceMaintainer = InstanceMaintainer(database)
        return true
    }

    override fun shutdown() {
        database.close()
    }


    // --- helpers ------------------------------------------------------------------------------

    private fun specFor(match: Int) =
        TABLE_SPECS[match] ?: throw IllegalArgumentException("Unsupported URI")

    private fun Uri.isSyncAdapter(): Boolean =
        getBooleanQueryParameter(TasksContract.PARAM_CALLER_IS_SYNCADAPTER, false)

    private fun validateColumns(values: ContentValues, allowed: Set<String>) {
        for (key in values.keySet())
            if (key !in allowed)
                throw IllegalArgumentException("Unknown/forbidden column: $key")
    }

    /**
     * Fills in the same defaults the Room entities declare (see `db/Entities.kt`) for `NOT NULL`
     * columns the caller didn't supply. Room's Kotlin-level default parameter values only apply
     * when constructing an entity through its constructor (DAO inserts) — a raw [ContentValues]
     * insert through this provider bypasses them entirely and would otherwise fail with a bare
     * [android.database.sqlite.SQLiteConstraintException] instead of just using a sane default,
     * exactly the way other Android providers (e.g. CalendarProvider2) behave for optional columns.
     */
    private fun applyInsertDefaults(match: Int, values: ContentValues, syncAdapter: Boolean) {
        when (match) {
            TASK_LISTS -> {
                if (!values.containsKey(TaskLists.ACCESS_LEVEL)) values.put(TaskLists.ACCESS_LEVEL, TaskLists.AccessLevel.READ_WRITE)
                if (!values.containsKey(TaskLists.VISIBLE)) values.put(TaskLists.VISIBLE, true)
                if (!values.containsKey(TaskLists.SYNC_ENABLED)) values.put(TaskLists.SYNC_ENABLED, true)
                if (!values.containsKey(TaskLists.SUPPORTED_COMPONENTS)) values.put(TaskLists.SUPPORTED_COMPONENTS, TaskLists.Component.VTODO)
            }
            TASKS -> {
                if (!values.containsKey(Tasks._DIRTY)) values.put(Tasks._DIRTY, !syncAdapter)
                if (!values.containsKey(Tasks._DELETED)) values.put(Tasks._DELETED, false)
                if (!values.containsKey(Tasks.STATUS)) values.put(Tasks.STATUS, Tasks.Status.NEEDS_ACTION)
                if (!values.containsKey(Tasks.PRIORITY)) values.put(Tasks.PRIORITY, 0)
                if (!values.containsKey(Tasks.SEQUENCE)) values.put(Tasks.SEQUENCE, 0)
                if (!values.containsKey(Tasks.IS_ALLDAY)) values.put(Tasks.IS_ALLDAY, false)
                if (!values.containsKey(Tasks.COMPLETED_ALLDAY)) values.put(Tasks.COMPLETED_ALLDAY, false)
                if (!values.containsKey(Tasks.ORIGINAL_INSTANCE_ALLDAY)) values.put(Tasks.ORIGINAL_INSTANCE_ALLDAY, false)
            }
            ALARMS -> if (!values.containsKey(TaskAlarms.ACTION)) values.put(TaskAlarms.ACTION, TaskAlarms.Action.DISPLAY)
        }
    }

    /** Appends the numeric ID from an item URI as an `AND id = ?` selection constraint. */
    private fun appendIdConstraint(
        uri: Uri, match: Int, spec: TableSpec, selection: String?, selectionArgs: Array<String>?
    ): Pair<String?, Array<String>?> {
        if (match !in ITEM_MATCHES) return selection to selectionArgs
        val id = ContentUris.parseId(uri)
        val newSelection = if (selection.isNullOrBlank())
            "${spec.idColumn} = ?"
        else
            "($selection) AND ${spec.idColumn} = ?"
        return newSelection to ((selectionArgs ?: emptyArray()) + id.toString())
    }

    private fun distinctLongColumn(table: String, column: String, selection: String?, args: Array<String>?): Set<Long> {
        val sql = buildString {
            append("SELECT DISTINCT ").append(column).append(" FROM ").append(table)
            if (!selection.isNullOrBlank()) append(" WHERE ").append(selection)
        }
        val bindArgs: Array<Any?> = args?.map { it as Any? }?.toTypedArray() ?: emptyArray()
        val result = mutableSetOf<Long>()
        database.query(SimpleSQLiteQuery(sql, bindArgs)).use { cursor ->
            while (cursor.moveToNext())
                if (!cursor.isNull(0))
                    result += cursor.getLong(0)
        }
        return result
    }

    private fun taskIdForAlarm(alarmId: Long): Long? =
        distinctLongColumn(TaskAlarms.PATH, TaskAlarms.TASK_ID, "${TaskAlarms._ID} = ?", arrayOf(alarmId.toString()))
            .firstOrNull()

    /**
     * Resolves each of [taskIds] to its recurrence family root ([Tasks.ORIGINAL_INSTANCE_ID] if
     * it's a RECURRENCE-ID exception row, itself otherwise) — the id [InstanceMaintainer.refreshFamily]
     * expects. Looked up fresh (not from caller-supplied values) so it reflects the current DB state.
     */
    private fun rootsForTaskIds(taskIds: Set<Long>): Set<Long> {
        if (taskIds.isEmpty()) return emptySet()
        val placeholders = taskIds.joinToString(",") { "?" }
        val sql = "SELECT ${Tasks._ID}, ${Tasks.ORIGINAL_INSTANCE_ID} FROM ${Tasks.PATH} WHERE ${Tasks._ID} IN ($placeholders)"
        val bindArgs: Array<Any?> = taskIds.map { it as Any? }.toTypedArray()
        val roots = mutableSetOf<Long>()
        database.query(SimpleSQLiteQuery(sql, bindArgs)).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val originalInstanceId = if (cursor.isNull(1)) null else cursor.getLong(1)
                roots += originalInstanceId ?: id
            }
        }
        return roots
    }

    /** Dirty propagation from a sub-row to its parent task (§4) — skipped for sync-adapter writes,
     * which represent data that just came from the server and is therefore not locally modified. */
    private fun markTaskDirty(taskId: Long) {
        val cv = ContentValues().apply { put(Tasks._DIRTY, true) }
        database.openHelper.writableDatabase.update(Tasks.PATH, android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, cv, "${Tasks._ID} = ?", arrayOf(taskId.toString()))
    }

    private fun notify(uri: Uri, syncAdapter: Boolean) {
        val flags = if (syncAdapter) 0 else android.content.ContentResolver.NOTIFY_SYNC_TO_NETWORK
        context?.contentResolver?.notifyChange(uri, null, flags)
    }


    // --- ContentProvider implementation --------------------------------------------------------

    override fun getType(uri: Uri): String {
        val match = matcher.match(uri)
        val spec = specFor(match)
        val isItem = match in ITEM_MATCHES
        return if (isItem)
            "vnd.android.cursor.item/vnd.at.bitfire.tasks.${spec.tableName}"
        else
            "vnd.android.cursor.dir/vnd.at.bitfire.tasks.${spec.tableName}"
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val match = matcher.match(uri)
        val spec = specFor(match)

        val (finalSelection, finalArgs) = appendIdConstraint(uri, match, spec, selection, selectionArgs?.toList()?.toTypedArray())

        val qb = SQLiteQueryBuilder().apply {
            tables = spec.tableName
            projectionMap = spec.allowedColumns.associateWith { it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                setStrict(true)
        }
        val sql = qb.buildQuery(projection?.toList()?.toTypedArray(), finalSelection, null, null, sortOrder, null)
        val bindArgs: Array<Any?> = finalArgs?.map { it as Any? }?.toTypedArray() ?: emptyArray()

        val cursor = database.query(SimpleSQLiteQuery(sql, bindArgs))
        context?.contentResolver?.let { cursor.setNotificationUri(it, uri) }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val match = matcher.match(uri)
        if (match !in DIR_MATCHES)
            throw IllegalArgumentException("Cannot insert into item URI: $uri")
        if (match in READ_ONLY_MATCHES)
            throw UnsupportedOperationException("${TaskInstances.PATH} is maintained by the provider, not writable")

        val spec = specFor(match)
        val syncAdapter = uri.isSyncAdapter()
        val cv = ContentValues(values ?: ContentValues())
        validateColumns(cv, spec.allowedColumns)
        applyInsertDefaults(match, cv, syncAdapter)

        if (match == TASKS && !syncAdapter) {
            cv.put(Tasks._DIRTY, true)
            cv.put(Tasks._DELETED, false)
        }

        val id = database.openHelper.writableDatabase.insert(spec.tableName, android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, cv)

        if (!syncAdapter) {
            when (match) {
                PROPERTIES -> cv.getAsLong(TaskProperties.TASK_ID)?.let(::markTaskDirty)
                ALARMS -> cv.getAsLong(TaskAlarms.TASK_ID)?.let(::markTaskDirty)
                ALARM_PROPERTIES -> cv.getAsLong(AlarmProperties.ALARM_ID)?.let { taskIdForAlarm(it)?.let(::markTaskDirty) }
            }
        }

        if (match == TASKS)
            instanceMaintainer.refreshFamily(cv.getAsLong(Tasks.ORIGINAL_INSTANCE_ID) ?: id)

        notify(uri, syncAdapter)
        return ContentUris.withAppendedId(TasksContract.contentUri(requireNotNull(uri.authority), spec.tableName), id)
    }

    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>): Int {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            for (v in values)
                insert(uri, v)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return values.size
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val match = matcher.match(uri)
        if (match in READ_ONLY_MATCHES)
            throw UnsupportedOperationException("${TaskInstances.PATH} is maintained by the provider, not writable")

        val spec = specFor(match)
        val syncAdapter = uri.isSyncAdapter()
        val cv = ContentValues(values ?: ContentValues())
        validateColumns(cv, spec.allowedColumns)

        if ((match == TASKS || match == TASK_ID) && !syncAdapter)
            cv.put(Tasks._DIRTY, true)

        val (finalSelection, finalArgs) = appendIdConstraint(uri, match, spec, selection, selectionArgs?.toList()?.toTypedArray())

        // capture dirty-propagation targets before mutating (selection still resolves to the pre-update rows;
        // task_id/alarm_id are not among the columns a caller is expected to change mid-flight)
        val dirtyTaskIds = if (!syncAdapter) when (match) {
            PROPERTIES, PROPERTY_ID -> distinctLongColumn(TaskProperties.PATH, TaskProperties.TASK_ID, finalSelection, finalArgs)
            ALARMS, ALARM_ID -> distinctLongColumn(TaskAlarms.PATH, TaskAlarms.TASK_ID, finalSelection, finalArgs)
            ALARM_PROPERTIES, ALARM_PROPERTY_ID ->
                distinctLongColumn(AlarmProperties.PATH, AlarmProperties.ALARM_ID, finalSelection, finalArgs)
                    .mapNotNull(::taskIdForAlarm).toSet()
            else -> emptySet()
        } else emptySet()

        // capture affected tasks' family roots before mutating too - a caller can change which
        // family a row belongs to (Tasks.ORIGINAL_INSTANCE_ID), so both the old and new root need
        // a refresh; looking up by the fixed id set (not re-running finalSelection) survives that
        val affectedTaskIds = if (match == TASKS || match == TASK_ID) distinctLongColumn(Tasks.PATH, Tasks._ID, finalSelection, finalArgs) else emptySet()
        val preRoots = rootsForTaskIds(affectedTaskIds)

        val count = database.openHelper.writableDatabase.update(spec.tableName, android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, cv, finalSelection, finalArgs)

        if (count > 0) {
            dirtyTaskIds.forEach(::markTaskDirty)
            (preRoots + rootsForTaskIds(affectedTaskIds)).forEach(instanceMaintainer::refreshFamily)
            notify(uri, syncAdapter)
        }
        return count
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val match = matcher.match(uri)
        if (match in READ_ONLY_MATCHES)
            throw UnsupportedOperationException("${TaskInstances.PATH} is maintained by the provider, not writable")

        val spec = specFor(match)
        val syncAdapter = uri.isSyncAdapter()
        val (finalSelection, finalArgs) = appendIdConstraint(uri, match, spec, selection, selectionArgs?.toList()?.toTypedArray())

        val dirtyTaskIds = if (!syncAdapter) when (match) {
            PROPERTIES, PROPERTY_ID -> distinctLongColumn(TaskProperties.PATH, TaskProperties.TASK_ID, finalSelection, finalArgs)
            ALARMS, ALARM_ID -> distinctLongColumn(TaskAlarms.PATH, TaskAlarms.TASK_ID, finalSelection, finalArgs)
            ALARM_PROPERTIES, ALARM_PROPERTY_ID ->
                distinctLongColumn(AlarmProperties.PATH, AlarmProperties.ALARM_ID, finalSelection, finalArgs)
                    .mapNotNull(::taskIdForAlarm).toSet()
            else -> emptySet()
        } else emptySet()

        // capture family roots before the row disappears (physical delete) or gets tombstoned -
        // either way, whatever family it belonged to needs its instances refreshed: a tombstoned/
        // removed exception's occurrence reverts to the main task, and a removed main's family
        // simply produces nothing once refreshed (its rows are gone by then, refresh is then a no-op)
        val affectedRoots = if (match == TASKS || match == TASK_ID)
            rootsForTaskIds(distinctLongColumn(Tasks.PATH, Tasks._ID, finalSelection, finalArgs))
        else emptySet()

        // Tombstone instead of physical delete for non-sync-adapter deletes of tasks (§4): the
        // deletion still needs to be uploaded. Sync-adapter deletes (after successful upload, or
        // during a clean download) really remove the row, cascading to properties/alarms/instances.
        val count = if ((match == TASKS || match == TASK_ID) && !syncAdapter) {
            val cv = ContentValues().apply {
                put(Tasks._DELETED, true)
                put(Tasks._DIRTY, true)
            }
            database.openHelper.writableDatabase.update(Tasks.PATH, android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, cv, finalSelection, finalArgs)
        } else {
            database.openHelper.writableDatabase.delete(spec.tableName, finalSelection, finalArgs)
        }

        if (count > 0) {
            dirtyTaskIds.forEach(::markTaskDirty)
            affectedRoots.forEach(instanceMaintainer::refreshFamily)
            notify(uri, syncAdapter)
        }
        return count
    }

    @Throws(OperationApplicationException::class)
    override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            val results = super.applyBatch(operations)
            db.setTransactionSuccessful()
            return results
        } finally {
            db.endTransaction()
        }
    }

}
