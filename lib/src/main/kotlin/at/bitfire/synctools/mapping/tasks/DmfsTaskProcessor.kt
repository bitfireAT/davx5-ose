/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks

import android.content.ContentValues
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.mapping.tasks.handler.AlarmsHandler
import at.bitfire.synctools.mapping.tasks.handler.ColorHandler
import at.bitfire.synctools.mapping.tasks.handler.DescriptionHandler
import at.bitfire.synctools.mapping.tasks.handler.DmfsTaskFieldHandler
import at.bitfire.synctools.mapping.tasks.handler.DmfsTaskPropertyHandler
import at.bitfire.synctools.mapping.tasks.handler.DueHandler
import at.bitfire.synctools.mapping.tasks.handler.DurationHandler
import at.bitfire.synctools.mapping.tasks.handler.GeoHandler
import at.bitfire.synctools.mapping.tasks.handler.LocationHandler
import at.bitfire.synctools.mapping.tasks.handler.OrganizerHandler
import at.bitfire.synctools.mapping.tasks.handler.SequenceHandler
import at.bitfire.synctools.mapping.tasks.handler.StartTimeHandler
import at.bitfire.synctools.mapping.tasks.handler.TitleHandler
import at.bitfire.synctools.mapping.tasks.handler.UidHandler
import at.bitfire.synctools.mapping.tasks.handler.UrlHandler
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.UNKNOWN_PROPERTY_DATA
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Status
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.Alarm
import org.dmfs.tasks.contract.TaskContract.Property.Category
import org.dmfs.tasks.contract.TaskContract.Property.Comment
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.Tasks
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

    private val fieldHandlers: Array<DmfsTaskFieldHandler> = arrayOf(
        UidHandler(),
        TitleHandler(),
        SequenceHandler(),
        StartTimeHandler(),
        DueHandler(),
        DurationHandler(),
        DescriptionHandler(),
        LocationHandler(),
        GeoHandler(),
        ColorHandler(),
        UrlHandler(),
        OrganizerHandler(),
    )

    private val propertyHandlers: Map<String, DmfsTaskPropertyHandler> = mapOf(
        Alarm.CONTENT_ITEM_TYPE to AlarmsHandler(),
    )

    private val logger
        get() = Logger.getLogger(javaClass.name)

    fun populateTask(values: ContentValues, to: Task) {
        for (handler in fieldHandlers)
            handler.process(values, to)

        to.userAgents += taskList.providerName.packageName

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

        values.getAsLong(Tasks.CREATED)?.let { to.createdAt = it }
        values.getAsLong(Tasks.LAST_MODIFIED)?.let { to.lastModified = it }

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

        val type = row.getAsString(Properties.MIMETYPE)
        val handler = propertyHandlers[type]
        if (handler != null) {
            handler.process(row, to)
            return
        }

        when (type) {
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