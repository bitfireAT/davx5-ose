/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.tasks.provider.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import at.bitfire.tasks.contract.AlarmProperties
import at.bitfire.tasks.contract.TaskAlarms
import at.bitfire.tasks.contract.TaskLists
import at.bitfire.tasks.contract.TaskProperties
import at.bitfire.tasks.contract.Tasks

/**
 * Room representation of [TaskLists]. Column names are the exact contract column names, so the
 * DB schema and the public [android.content.ContentProvider] contract can never drift apart.
 */
@Entity(tableName = TaskLists.PATH)
data class TaskListEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TaskLists._ID) val id: Long = 0,

    @ColumnInfo(name = TaskLists.ACCOUNT_NAME) val accountName: String,
    @ColumnInfo(name = TaskLists.ACCOUNT_TYPE) val accountType: String,

    @ColumnInfo(name = TaskLists._SYNC_ID) val syncId: String? = null,
    @ColumnInfo(name = TaskLists.SYNC_VERSION) val syncVersion: String? = null,
    @ColumnInfo(name = TaskLists.SYNC1) val sync1: String? = null,
    @ColumnInfo(name = TaskLists.SYNC2) val sync2: String? = null,
    @ColumnInfo(name = TaskLists.SYNC3) val sync3: String? = null,
    @ColumnInfo(name = TaskLists.SYNC4) val sync4: String? = null,

    @ColumnInfo(name = TaskLists.LIST_NAME) val listName: String? = null,
    @ColumnInfo(name = TaskLists.LIST_DESCRIPTION) val listDescription: String? = null,
    @ColumnInfo(name = TaskLists.LIST_COLOR) val listColor: Int? = null,
    @ColumnInfo(name = TaskLists.LIST_OWNER) val listOwner: String? = null,
    @ColumnInfo(name = TaskLists.ACCESS_LEVEL) val accessLevel: Int = TaskLists.AccessLevel.READ_WRITE,
    @ColumnInfo(name = TaskLists.VISIBLE) val visible: Boolean = true,
    @ColumnInfo(name = TaskLists.SYNC_ENABLED) val syncEnabled: Boolean = true,
    @ColumnInfo(name = TaskLists.SUPPORTED_COMPONENTS) val supportedComponents: String = TaskLists.Component.VTODO
)

/**
 * Room representation of [Tasks] — one row per VTODO component (main occurrence or a
 * RECURRENCE-ID override, see [originalInstanceId]).
 */
@Entity(
    tableName = Tasks.PATH,
    foreignKeys = [
        ForeignKey(
            entity = TaskListEntity::class,
            parentColumns = [TaskLists._ID],
            childColumns = [Tasks.LIST_ID],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = [Tasks._ID],
            childColumns = [Tasks.ORIGINAL_INSTANCE_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(Tasks.LIST_ID),
        Index(Tasks.ORIGINAL_INSTANCE_ID),
        Index(Tasks._UID)
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = Tasks._ID) val id: Long = 0,
    @ColumnInfo(name = Tasks.LIST_ID) val listId: Long,

    @ColumnInfo(name = Tasks._UID) val uid: String,
    @ColumnInfo(name = Tasks._SYNC_ID) val syncId: String? = null,
    @ColumnInfo(name = Tasks._DIRTY) val dirty: Boolean = true,
    @ColumnInfo(name = Tasks._DELETED) val deleted: Boolean = false,
    @ColumnInfo(name = Tasks.SYNC_VERSION) val syncVersion: String? = null,
    @ColumnInfo(name = Tasks.SYNC1) val sync1: String? = null,
    @ColumnInfo(name = Tasks.SYNC2) val sync2: String? = null,
    @ColumnInfo(name = Tasks.SYNC3) val sync3: String? = null,
    @ColumnInfo(name = Tasks.SYNC4) val sync4: String? = null,

    @ColumnInfo(name = Tasks.ORIGINAL_INSTANCE_ID) val originalInstanceId: Long? = null,
    @ColumnInfo(name = Tasks.ORIGINAL_INSTANCE_TIME) val originalInstanceTime: Long? = null,
    @ColumnInfo(name = Tasks.ORIGINAL_INSTANCE_ALLDAY) val originalInstanceAllDay: Boolean = false,

    @ColumnInfo(name = Tasks.SUMMARY) val summary: String? = null,
    @ColumnInfo(name = Tasks.DESCRIPTION) val description: String? = null,
    @ColumnInfo(name = Tasks.LOCATION) val location: String? = null,
    @ColumnInfo(name = Tasks.GEO_LAT) val geoLat: Double? = null,
    @ColumnInfo(name = Tasks.GEO_LON) val geoLon: Double? = null,
    @ColumnInfo(name = Tasks.URL) val url: String? = null,
    @ColumnInfo(name = Tasks.COLOR) val color: String? = null,

    @ColumnInfo(name = Tasks.ORGANIZER) val organizer: String? = null,
    @ColumnInfo(name = Tasks.ORGANIZER_CN) val organizerCn: String? = null,
    @ColumnInfo(name = Tasks.ORGANIZER_SENT_BY) val organizerSentBy: String? = null,

    @ColumnInfo(name = Tasks.PRIORITY) val priority: Int = 0,
    @ColumnInfo(name = Tasks.CLASSIFICATION) val classification: String? = null,
    @ColumnInfo(name = Tasks.STATUS) val status: String = Tasks.Status.NEEDS_ACTION,
    @ColumnInfo(name = Tasks.PERCENT_COMPLETE) val percentComplete: Int? = null,
    @ColumnInfo(name = Tasks.COMPLETED) val completed: Long? = null,
    @ColumnInfo(name = Tasks.COMPLETED_ALLDAY) val completedAllDay: Boolean = false,

    @ColumnInfo(name = Tasks.SEQUENCE) val sequence: Int = 0,
    @ColumnInfo(name = Tasks.CREATED) val created: Long? = null,
    @ColumnInfo(name = Tasks.LAST_MODIFIED) val lastModified: Long? = null,
    @ColumnInfo(name = Tasks.DTSTAMP) val dtstamp: Long? = null,

    @ColumnInfo(name = Tasks.DTSTART) val dtstart: Long? = null,
    @ColumnInfo(name = Tasks.DTSTART_TZ) val dtstartTz: String? = null,
    @ColumnInfo(name = Tasks.DUE) val due: Long? = null,
    @ColumnInfo(name = Tasks.DUE_TZ) val dueTz: String? = null,
    @ColumnInfo(name = Tasks.DURATION) val duration: String? = null,
    @ColumnInfo(name = Tasks.IS_ALLDAY) val isAllDay: Boolean = false,

    @ColumnInfo(name = Tasks.RRULE) val rrule: String? = null,
    @ColumnInfo(name = Tasks.RDATE) val rdate: String? = null,
    @ColumnInfo(name = Tasks.EXDATE) val exdate: String? = null
)

/**
 * Room representation of a [TaskProperties] sub-row (`ContactsContract`-style generic
 * `data1..dataN` columns, D4).
 */
@Entity(
    tableName = TaskProperties.PATH,
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = [Tasks._ID],
            childColumns = [TaskProperties.TASK_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(TaskProperties.TASK_ID)]
)
data class TaskPropertyEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TaskProperties._ID) val id: Long = 0,
    @ColumnInfo(name = TaskProperties.TASK_ID) val taskId: Long,
    @ColumnInfo(name = TaskProperties.MIMETYPE) val mimetype: String,

    @ColumnInfo(name = TaskProperties.DATA1) val data1: String? = null,
    @ColumnInfo(name = TaskProperties.DATA2) val data2: String? = null,
    @ColumnInfo(name = TaskProperties.DATA3) val data3: String? = null,
    @ColumnInfo(name = TaskProperties.DATA4) val data4: String? = null,
    @ColumnInfo(name = TaskProperties.DATA5) val data5: String? = null,
    @ColumnInfo(name = TaskProperties.DATA6) val data6: String? = null,
    @ColumnInfo(name = TaskProperties.DATA7) val data7: String? = null,
    @ColumnInfo(name = TaskProperties.DATA8) val data8: String? = null,
    @ColumnInfo(name = TaskProperties.DATA9) val data9: String? = null,
    @ColumnInfo(name = TaskProperties.DATA10) val data10: String? = null,
    @ColumnInfo(name = TaskProperties.DATA11) val data11: String? = null,
    @ColumnInfo(name = TaskProperties.DATA12) val data12: String? = null,
    @ColumnInfo(name = TaskProperties.DATA13) val data13: String? = null,
    @ColumnInfo(name = TaskProperties.DATA14) val data14: String? = null,
    @ColumnInfo(name = TaskProperties.DATA15) val data15: String? = null,

    @ColumnInfo(name = TaskProperties.DATA_SYNC1) val dataSync1: String? = null,
    @ColumnInfo(name = TaskProperties.DATA_SYNC2) val dataSync2: String? = null,
    @ColumnInfo(name = TaskProperties.DATA_SYNC3) val dataSync3: String? = null,
    @ColumnInfo(name = TaskProperties.DATA_SYNC4) val dataSync4: String? = null
)

/**
 * Room representation of [TaskAlarms] (RFC 5545 §3.6.6 VALARM).
 */
@Entity(
    tableName = TaskAlarms.PATH,
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = [Tasks._ID],
            childColumns = [TaskAlarms.TASK_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(TaskAlarms.TASK_ID)]
)
data class TaskAlarmEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = TaskAlarms._ID) val id: Long = 0,
    @ColumnInfo(name = TaskAlarms.TASK_ID) val taskId: Long,

    @ColumnInfo(name = TaskAlarms.ACTION) val action: String = TaskAlarms.Action.DISPLAY,
    @ColumnInfo(name = TaskAlarms.TRIGGER_RELATIVE) val triggerRelative: String? = null,
    @ColumnInfo(name = TaskAlarms.TRIGGER_RELATED) val triggerRelated: String? = null,
    @ColumnInfo(name = TaskAlarms.TRIGGER_ABSOLUTE) val triggerAbsolute: Long? = null,
    @ColumnInfo(name = TaskAlarms.DURATION) val duration: String? = null,
    @ColumnInfo(name = TaskAlarms.REPEAT) val repeat: Int? = null,
    @ColumnInfo(name = TaskAlarms.DESCRIPTION) val description: String? = null,
    @ColumnInfo(name = TaskAlarms.SUMMARY) val summary: String? = null,
    @ColumnInfo(name = TaskAlarms.UID) val uid: String? = null,
    @ColumnInfo(name = TaskAlarms.ACKNOWLEDGED) val acknowledged: Long? = null,
    @ColumnInfo(name = TaskAlarms.RELATED_TO) val relatedTo: String? = null,
    @ColumnInfo(name = TaskAlarms.PROXIMITY) val proximity: String? = null
)

/**
 * Room representation of an [AlarmProperties] sub-row (alarm ATTENDEE/ATTACH).
 */
@Entity(
    tableName = AlarmProperties.PATH,
    foreignKeys = [
        ForeignKey(
            entity = TaskAlarmEntity::class,
            parentColumns = [TaskAlarms._ID],
            childColumns = [AlarmProperties.ALARM_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(AlarmProperties.ALARM_ID)]
)
data class AlarmPropertyEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = AlarmProperties._ID) val id: Long = 0,
    @ColumnInfo(name = AlarmProperties.ALARM_ID) val alarmId: Long,
    @ColumnInfo(name = AlarmProperties.MIMETYPE) val mimetype: String,

    @ColumnInfo(name = AlarmProperties.DATA1) val data1: String? = null,
    @ColumnInfo(name = AlarmProperties.DATA2) val data2: String? = null,
    @ColumnInfo(name = AlarmProperties.DATA3) val data3: String? = null
)

/**
 * Room representation of [at.bitfire.tasks.contract.TaskInstances]. Maintained by the provider
 * on every write to [TaskEntity] (see
 * [at.bitfire.davdroid.tasks.provider.recurrence.InstanceMaintainer]); read-only from the outside.
 */
@Entity(
    tableName = at.bitfire.tasks.contract.TaskInstances.PATH,
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = [Tasks._ID],
            childColumns = [at.bitfire.tasks.contract.TaskInstances.TASK_ID],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(at.bitfire.tasks.contract.TaskInstances.TASK_ID)]
)
data class TaskInstanceEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = at.bitfire.tasks.contract.TaskInstances._ID) val id: Long = 0,
    @ColumnInfo(name = at.bitfire.tasks.contract.TaskInstances.TASK_ID) val taskId: Long,

    @ColumnInfo(name = at.bitfire.tasks.contract.TaskInstances.INSTANCE_START) val instanceStart: Long? = null,
    @ColumnInfo(name = at.bitfire.tasks.contract.TaskInstances.INSTANCE_DUE) val instanceDue: Long? = null,
    @ColumnInfo(name = at.bitfire.tasks.contract.TaskInstances.INSTANCE_START_SORTING) val instanceStartSorting: Long? = null,
    @ColumnInfo(name = at.bitfire.tasks.contract.TaskInstances.INSTANCE_DUE_SORTING) val instanceDueSorting: Long? = null,
    @ColumnInfo(name = at.bitfire.tasks.contract.TaskInstances.DISTANCE_FROM_CURRENT) val distanceFromCurrent: Long = 0
)
