/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks

import android.content.ContentValues
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.UNKNOWN_PROPERTY_DATA
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.Geo
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Trigger
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.Alarm
import org.dmfs.tasks.contract.TaskContract.Property.Category
import org.dmfs.tasks.contract.TaskContract.Property.Comment
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.net.URISyntaxException
import java.time.Instant
import java.time.temporal.Temporal
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Reads dmfs task provider data rows into a [Task]
 * (former DmfsTask "populate..." methods).
 */
class DmfsTaskProcessor(
    private val taskList: DmfsTaskList
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    fun populateTask(values: ContentValues, to: Task) {
        to.uid = values.getAsString(Tasks._UID)
        to.sequence = values.getAsInteger(Tasks.SYNC_VERSION)
        to.summary = values.getAsString(Tasks.TITLE)
        to.location = values.getAsString(Tasks.LOCATION)
        to.userAgents += taskList.providerName.packageName

        values.getAsString(Tasks.GEO)?.takeIf { it.contains(",") }?.let { geo ->
            val (lng, lat) = geo.split(',')
            try {
                to.geoPosition = Geo(lat.toBigDecimal(), lng.toBigDecimal())
            } catch (e: NumberFormatException) {
                logger.log(Level.WARNING, "Invalid GEO value: $geo", e)
            }
        }

        to.description = values.getAsString(Tasks.DESCRIPTION)
        to.color = values.getAsInteger(Tasks.TASK_COLOR)
        to.url = values.getAsString(Tasks.URL)

        values.getAsString(Tasks.ORGANIZER)?.let {
            try {
                to.organizer = Organizer("mailto:$it")
            } catch(e: URISyntaxException) {
                logger.log(Level.WARNING, "Invalid ORGANIZER email", e)
            }
        }

        values.getAsInteger(Tasks.PRIORITY)?.let { to.priority = it }

        // Note: big method – maybe split? Depends on how we want to proceed with refactoring.

        to.classification = when (values.getAsInteger(Tasks.CLASSIFICATION)) {
            Tasks.CLASSIFICATION_PUBLIC ->       Clazz(Clazz.VALUE_PUBLIC)
            Tasks.CLASSIFICATION_PRIVATE ->      Clazz(Clazz.VALUE_PRIVATE)
            Tasks.CLASSIFICATION_CONFIDENTIAL -> Clazz(Clazz.VALUE_CONFIDENTIAL)
            else ->                              null
        }

        values.getAsLong(Tasks.COMPLETED)?.let { to.completedAt = Completed(Instant.ofEpochMilli(it)) }
        values.getAsInteger(Tasks.PERCENT_COMPLETE)?.let { to.percentComplete = it }

        to.status = when (values.getAsInteger(Tasks.STATUS)) {
            Tasks.STATUS_IN_PROCESS -> Status(Status.VALUE_IN_PROCESS)
            Tasks.STATUS_COMPLETED ->  Status(Status.VALUE_COMPLETED)
            Tasks.STATUS_CANCELLED ->  Status(Status.VALUE_CANCELLED)
            else ->                    Status(Status.VALUE_NEEDS_ACTION)
        }

        val allDay = (values.getAsInteger(Tasks.IS_ALLDAY) ?: 0) != 0

        val tzID = values.getAsString(Tasks.TZ)
        val tz = tzID?.let {
            val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
            tzRegistry.getTimeZone(it)
        }

        values.getAsLong(Tasks.CREATED)?.let { to.createdAt = it }
        values.getAsLong(Tasks.LAST_MODIFIED)?.let { to.lastModified = it }

        values.getAsLong(Tasks.DTSTART)?.let { dtStart ->
            val instant = Instant.ofEpochMilli(dtStart)
            to.dtStart =
                if (allDay)
                    DtStart(instant.toLocalDate())
                else {
                    if (tz == null)
                        DtStart(instant)
                    else
                        DtStart(instant.atZone(tz.toZoneId()))
                }
        }

        values.getAsLong(Tasks.DUE)?.let { due ->
            val instant = Instant.ofEpochMilli(due)
            to.due =
                if (allDay)
                    Due(instant.toLocalDate())
                else {
                    if (tz == null)
                        Due(instant)
                    else
                        Due(instant.atZone(tz.toZoneId()))
                }
        }

        values.getAsString(Tasks.DURATION)?.let { duration ->
            val fixedDuration = AndroidTimeUtils.parseDuration(duration)
            to.duration = Duration(fixedDuration)
        }

        values.getAsString(Tasks.RDATE)?.let { rdateStr ->
            AndroidTimeUtils.androidStringToRecurrenceSet(rdateStr, allDay) { dates -> RDate(dates) }?.let { to.rDates += it }
        }
        values.getAsString(Tasks.EXDATE)?.let { exdateStr ->
            AndroidTimeUtils.androidStringToRecurrenceSet(exdateStr, allDay) { dates -> ExDate(dates) }?.let { to.exDates += it }
        }

        values.getAsString(Tasks.RRULE)?.let { to.rRule = RRule<Temporal>(it) }
    }

    fun populateProperty(row: ContentValues, to: Task) {
        logger.log(Level.FINER, "Found property", row)

        when (val type = row.getAsString(Properties.MIMETYPE)) {
            Alarm.CONTENT_ITEM_TYPE ->
                populateAlarm(row, to)
            Category.CONTENT_ITEM_TYPE ->
                to.categories += row.getAsString(Category.CATEGORY_NAME)
            Comment.CONTENT_ITEM_TYPE ->
                to.comment = row.getAsString(Comment.COMMENT)
            Relation.CONTENT_ITEM_TYPE ->
                populateRelatedTo(row, to)
            UnknownProperty.CONTENT_ITEM_TYPE ->
                to.unknownProperties += UnknownProperty.fromJsonString(row.getAsString(UNKNOWN_PROPERTY_DATA))
            else ->
                logger.warning("Found unknown property of type $type")
        }
    }

    private fun populateAlarm(row: ContentValues, to: Task) {
        val props = propertyListOf(
            Trigger(java.time.Duration.ofMinutes(-row.getAsLong(Alarm.MINUTES_BEFORE))).let {
                when (row.getAsInteger(Alarm.REFERENCE)) {
                    Alarm.ALARM_REFERENCE_START_DATE -> it.add(Related.START)
                    Alarm.ALARM_REFERENCE_DUE_DATE -> it.add(Related.END)
                    else -> it
                }
            },
            Action(
                when (row.getAsInteger(Alarm.ALARM_TYPE)) {
                    Alarm.ALARM_TYPE_EMAIL -> Action.VALUE_EMAIL
                    Alarm.ALARM_TYPE_SOUND -> Action.VALUE_AUDIO
                    // show alarm by default
                    else -> Action.VALUE_DISPLAY
                }
            ),
            Description(row.getAsString(Alarm.MESSAGE) ?: to.summary)
        )

        to.alarms += VAlarm(props)
    }

    private fun populateRelatedTo(row: ContentValues, to: Task) {
        val uid = row.getAsString(Relation.RELATED_UID)
        if (uid == null) {
            logger.warning("Task relation doesn't refer to same task list; can't be synchronized")
            return
        }

        to.relatedTo.add(
            RelatedTo(uid)
                // add relation type as reltypeparam
                .add(
                    when (row.getAsInteger(Relation.RELATED_TYPE)) {
                        Relation.RELTYPE_CHILD ->
                            RelType.CHILD
                        Relation.RELTYPE_SIBLING ->
                            RelType.SIBLING
                        else /* Relation.RELTYPE_PARENT, default value */ ->
                            RelType.PARENT
                    }
                )
        )
    }

}