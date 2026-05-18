/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.mapping.tasks.builder.AllDayBuilder
import at.bitfire.synctools.mapping.tasks.builder.ColorBuilder
import at.bitfire.synctools.mapping.tasks.builder.DescriptionBuilder
import at.bitfire.synctools.mapping.tasks.builder.DirtyBuilder
import at.bitfire.synctools.mapping.tasks.builder.DmfsTaskFieldBuilder
import at.bitfire.synctools.mapping.tasks.builder.DueBuilder
import at.bitfire.synctools.mapping.tasks.builder.DurationBuilder
import at.bitfire.synctools.mapping.tasks.builder.ETagBuilder
import at.bitfire.synctools.mapping.tasks.builder.GeoBuilder
import at.bitfire.synctools.mapping.tasks.builder.LocationBuilder
import at.bitfire.synctools.mapping.tasks.builder.OrganizerBuilder
import at.bitfire.synctools.mapping.tasks.builder.RecurrenceFieldsBuilder
import at.bitfire.synctools.mapping.tasks.builder.SequenceBuilder
import at.bitfire.synctools.mapping.tasks.builder.StartTimeBuilder
import at.bitfire.synctools.mapping.tasks.builder.SyncFlagsBuilder
import at.bitfire.synctools.mapping.tasks.builder.SyncIdBuilder
import at.bitfire.synctools.mapping.tasks.builder.TitleBuilder
import at.bitfire.synctools.mapping.tasks.builder.UidBuilder
import at.bitfire.synctools.mapping.tasks.builder.UrlBuilder
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.UNKNOWN_PROPERTY_DATA
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.TasksBatchOperation
import at.bitfire.synctools.util.AlarmTriggerCalculator
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.immutable.ImmutableAction
import net.fortuna.ical4j.model.property.immutable.ImmutableClazz
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.Alarm
import org.dmfs.tasks.contract.TaskContract.Property.Category
import org.dmfs.tasks.contract.TaskContract.Property.Comment
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.Tasks
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

    private val fieldBuilders: Array<DmfsTaskFieldBuilder> = arrayOf(
        // main task row fields
        UidBuilder(),
        SyncIdBuilder(syncId),
        ETagBuilder(eTag),
        SyncFlagsBuilder(flags),
        SequenceBuilder(),
        DirtyBuilder(),
        // content fields
        TitleBuilder(),
        DescriptionBuilder(),
        LocationBuilder(),
        GeoBuilder(),
        ColorBuilder(),
        UrlBuilder(),
        OrganizerBuilder(),
        // time fields and recurrence
        AllDayBuilder(),
        StartTimeBuilder(),
        DueBuilder(),
        DurationBuilder(),
        RecurrenceFieldsBuilder(),
        // status (still inline below)
        // property sub-rows (still inline below via insertProperties)
    )

    private val logger
        get() = Logger.getLogger(javaClass.name)

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

        // new builders

        val entity = Entity(ContentValues())
        for (fieldBuilder in fieldBuilders)
            fieldBuilder.build(task, entity)
        builder.withValues(entity.entityValues)

        // old builders

            // parent_id will be re-calculated when the relation row is inserted (if there is any)
            .withValue(Tasks.PARENT_ID, null)

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
        builder
            .withValue(Tasks.CREATED, task.createdAt)
            .withValue(Tasks.LAST_MODIFIED, task.lastModified)

        logger.log(Level.FINE, "Built task object", builder.build())
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
            val (alarmRef, minutes) = AlarmTriggerCalculator.alarmTriggerToMinutes(
                alarm = alarm,
                refStart = task.dtStart,
                refEnd = task.end,
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