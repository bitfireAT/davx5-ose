/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks

import at.bitfire.ical4android.ICalendar
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.COLUMN_ETAG
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.COLUMN_FLAGS
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.UNKNOWN_PROPERTY_DATA
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.TasksBatchOperation
import at.bitfire.synctools.util.AndroidTimeUtils
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.immutable.ImmutableAction
import net.fortuna.ical4j.model.property.immutable.ImmutableClazz
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus
import net.fortuna.ical4j.util.TimeZones
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.Alarm
import org.dmfs.tasks.contract.TaskContract.Property.Category
import org.dmfs.tasks.contract.TaskContract.Property.Comment
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.time.ZoneId
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

/**
 * Writes [at.bitfire.ical4android.Task] to dmfs task provider data rows
 * (former DmfsTask "build..." methods).
 */
class DmfsTaskBuilder(
    private val taskList: DmfsTaskList,
    private val task: Task,

    // DmfsTask-level fields
    private val id: Long?,
    private val syncId: String?,
    private val eTag: String?,
    private val flags: Int,
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

    fun addRows(batch: TasksBatchOperation): Int {
        val builder = CpoBuilder.newInsert(taskList.tasksUri())
        buildTask(builder, false)
        val idxTask = batch.nextBackrefIdx() // Get nextBackrefIdx BEFORE adding builder to batch
        batch += builder
        return idxTask
    }

    fun updateRows(batch: TasksBatchOperation) {
        val id = requireNotNull(id)
        val builder = CpoBuilder.newUpdate(taskList.taskUri(id))
        buildTask(builder, true)
        batch += builder
    }

    private fun buildTask(builder: CpoBuilder, update: Boolean) {
        if (!update)
            builder .withValue(Tasks.LIST_ID, taskList.id)

        builder .withValue(Tasks._UID, task.uid)
            .withValue(Tasks._DIRTY, 0)
            .withValue(Tasks.SYNC_VERSION, task.sequence)
            .withValue(Tasks.TITLE, task.summary)
            .withValue(Tasks.LOCATION, task.location)
            .withValue(Tasks.GEO, task.geoPosition?.let { "${it.longitude},${it.latitude}" })
            .withValue(Tasks.DESCRIPTION, task.description)
            .withValue(Tasks.TASK_COLOR, task.color)
            .withValue(Tasks.URL, task.url)

            .withValue(Tasks._SYNC_ID, syncId)
            .withValue(COLUMN_FLAGS, flags)
            .withValue(COLUMN_ETAG, eTag)

            // parent_id will be re-calculated when the relation row is inserted (if there is any)
            .withValue(Tasks.PARENT_ID, null)

        // organizer
        // Note: big method – maybe split? Depends on how we want to proceed with refactoring.

        task.organizer?.let { organizer ->
            val uri = organizer.calAddress
            val email = if (uri.scheme.equals("mailto", true))
                uri.schemeSpecificPart
            else
                organizer.getParameter<Email>(Parameter.EMAIL).getOrNull()?.value
            if (email != null)
                builder.withValue(Tasks.ORGANIZER, email)
            else
                logger.warning("Ignoring ORGANIZER without email address (not supported by Android)")
        }

        // Priority, classification
        builder
            .withValue(Tasks.PRIORITY, task.priority)
            .withValue(Tasks.CLASSIFICATION, when (task.classification?.value?.uppercase()) {
                ImmutableClazz.VALUE_PUBLIC -> Tasks.CLASSIFICATION_PUBLIC
                ImmutableClazz.VALUE_CONFIDENTIAL -> Tasks.CLASSIFICATION_CONFIDENTIAL
                null -> Tasks.CLASSIFICATION_DEFAULT
                else -> Tasks.CLASSIFICATION_PRIVATE    // all unknown classifications MUST be treated as PRIVATE
            })

        // COMPLETED must always be a DATE-TIME
        builder
            .withValue(Tasks.COMPLETED, task.completedAt?.date?.toEpochMilli())
            .withValue(Tasks.COMPLETED_IS_ALLDAY, 0)
            .withValue(Tasks.PERCENT_COMPLETE, task.percentComplete)

        // Status
        val status = when (task.status?.value) {
            ImmutableStatus.VALUE_IN_PROCESS -> Tasks.STATUS_IN_PROCESS
            ImmutableStatus.VALUE_COMPLETED  -> Tasks.STATUS_COMPLETED
            ImmutableStatus.VALUE_CANCELLED  -> Tasks.STATUS_CANCELLED
            else                             -> Tasks.STATUS_DEFAULT    // == Tasks.STATUS_NEEDS_ACTION
        }
        builder.withValue(Tasks.STATUS, status)

        // Time related
        val allDay = task.isAllDay()
        if (allDay) {
            builder .withValue(Tasks.IS_ALLDAY, 1)
                .withValue(Tasks.TZ, null)
        } else {
            task.dtStart = task.dtStart?.normalizedDate()?.let { DtStart(it) }
            task.due = task.due?.normalizedDate()?.let { Due(it) }
            builder .withValue(Tasks.IS_ALLDAY, 0)
                .withValue(Tasks.TZ, getTimeZone().id)
        }
        builder
            .withValue(Tasks.CREATED, task.createdAt)
            .withValue(Tasks.LAST_MODIFIED, task.lastModified)

            .withValue(Tasks.DTSTART, task.dtStart?.date?.toTimestamp())
            .withValue(Tasks.DUE, task.due?.date?.toTimestamp())
            .withValue(Tasks.DURATION, task.duration?.value)

            .withValue(Tasks.RDATE,
                if (task.rDates.isEmpty())
                    null
                else
                    AndroidTimeUtils.recurrenceSetsToOpenTasksString(task.rDates, if (allDay) null else getTimeZone()))
            .withValue(Tasks.RRULE, task.rRule?.value)

            .withValue(Tasks.EXDATE,
                if (task.exDates.isEmpty())
                    null
                else
                    AndroidTimeUtils.recurrenceSetsToOpenTasksString(task.exDates, if (allDay) null else getTimeZone()))

        logger.log(Level.FINE, "Built task object", builder.build())
    }

    fun getTimeZone(): TimeZone {
        var tzId = task.dtStart?.let { dtStart ->
            if (dtStart.isUtc)
                TimeZones.UTC_ID
            else
                dtStart.getParameter<TzId>(Parameter.TZID).getOrNull()?.value
        } ?:
        task.due?.let { due ->
            if (due.isUtc)
                TimeZones.UTC_ID
            else
                due.getParameter<TzId>(Parameter.TZID).getOrNull()?.value
        } ?:
        ZoneId.systemDefault().id

        // 'Z' is not a valid timezone id, replace it by the UTC definition
        if (tzId == "Z") tzId = TimeZones.UTC_ID

        val timeZone: TimeZone? = tzRegistry.getTimeZone(tzId)
        return timeZone ?: throw NullPointerException("Could not find timezone '$tzId' in registry.")
    }

    fun insertProperties(batch: TasksBatchOperation, idxTask: Int?) {
        insertAlarms(batch, idxTask)
        insertCategories(batch, idxTask)
        insertComment(batch, idxTask)
        insertRelatedTo(batch, idxTask)
        insertUnknownProperties(batch, idxTask)
    }

    private fun insertAlarms(batch: TasksBatchOperation, idxTask: Int?) {
        for (alarm in task.alarms) {
            val (alarmRef, minutes) = ICalendar.vAlarmToMin(
                alarm = alarm,
                refStart = task.dtStart,
                refEnd = task.due,
                refDuration = task.duration,
                allowRelEnd = true
            ) ?: continue
            val ref = when (alarmRef) {
                Related.END ->
                    Alarm.ALARM_REFERENCE_DUE_DATE
                else /* Related.START is the default value */ ->
                    Alarm.ALARM_REFERENCE_START_DATE
            }

            val alarmType = when (
                alarm.getProperty<Action>(Property.ACTION).getOrNull()?.value?.uppercase(Locale.ROOT)
            ) {
                ImmutableAction.VALUE_AUDIO   -> Alarm.ALARM_TYPE_SOUND
                ImmutableAction.VALUE_DISPLAY -> Alarm.ALARM_TYPE_MESSAGE
                ImmutableAction.VALUE_EMAIL   -> Alarm.ALARM_TYPE_EMAIL
                else                          -> Alarm.ALARM_TYPE_NOTHING
            }

            val builder = CpoBuilder
                .newInsert(taskList.tasksPropertiesUri())
                .withTaskId(Alarm.TASK_ID, idxTask)
                .withValue(Alarm.MIMETYPE, Alarm.CONTENT_ITEM_TYPE)
                .withValue(Alarm.MINUTES_BEFORE, minutes)
                .withValue(Alarm.REFERENCE, ref)
                .withValue(Alarm.MESSAGE, alarm.description?.value ?: alarm.summary)
                .withValue(Alarm.ALARM_TYPE, alarmType)

            logger.log(Level.FINE, "Inserting alarm", builder.build())
            batch += builder
        }
    }

    private fun insertCategories(batch: TasksBatchOperation, idxTask: Int?) {
        for (category in task.categories) {
            val builder = CpoBuilder.newInsert(taskList.tasksPropertiesUri())
                .withTaskId(Category.TASK_ID, idxTask)
                .withValue(Category.MIMETYPE, Category.CONTENT_ITEM_TYPE)
                .withValue(Category.CATEGORY_NAME, category)
            logger.log(Level.FINE, "Inserting category", builder.build())
            batch += builder
        }
    }

    private fun insertComment(batch: TasksBatchOperation, idxTask: Int?) {
        val comment = task.comment ?: return
        val builder = CpoBuilder.newInsert(taskList.tasksPropertiesUri())
            .withTaskId(Comment.TASK_ID, idxTask)
            .withValue(Comment.MIMETYPE, Comment.CONTENT_ITEM_TYPE)
            .withValue(Comment.COMMENT, comment)
        logger.log(Level.FINE, "Inserting comment", builder.build())
        batch += builder
    }

    private fun insertRelatedTo(batch: TasksBatchOperation, idxTask: Int?) {
        for (relatedTo in task.relatedTo) {
            val relType = when ((relatedTo.getParameter<RelType>(Parameter.RELTYPE)).getOrNull()) {
                RelType.CHILD                            -> Relation.RELTYPE_CHILD
                RelType.SIBLING                          -> Relation.RELTYPE_SIBLING
                else /* RelType.PARENT, default value */ -> Relation.RELTYPE_PARENT
            }
            val builder = CpoBuilder.newInsert(taskList.tasksPropertiesUri())
                .withTaskId(Relation.TASK_ID, idxTask)
                .withValue(Relation.MIMETYPE, Relation.CONTENT_ITEM_TYPE)
                .withValue(Relation.RELATED_UID, relatedTo.value)
                .withValue(Relation.RELATED_TYPE, relType)
            logger.log(Level.FINE, "Inserting relation", builder.build())
            batch += builder
        }
    }

    private fun insertUnknownProperties(batch: TasksBatchOperation, idxTask: Int?) {
        for (property in task.unknownProperties) {
            if (property.value.length > UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE) {
                logger.warning("Ignoring unknown property with ${property.value.length} octets (too long)")
                return
            }

            val builder = CpoBuilder.newInsert(taskList.tasksPropertiesUri())
                .withTaskId(Properties.TASK_ID, idxTask)
                .withValue(Properties.MIMETYPE, UnknownProperty.CONTENT_ITEM_TYPE)
                .withValue(UNKNOWN_PROPERTY_DATA, UnknownProperty.toJsonString(property))
            logger.log(Level.FINE, "Inserting unknown property", builder.build())
            batch += builder
        }
    }

    private fun CpoBuilder.withTaskId(column: String, idxTask: Int?): CpoBuilder {
        if (idxTask != null)
            withValueBackReference(column, idxTask)
        else
            withValue(column, requireNotNull(id))
        return this
    }

}