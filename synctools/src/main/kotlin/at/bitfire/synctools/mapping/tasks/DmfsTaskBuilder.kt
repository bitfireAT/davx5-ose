/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.AssociatedTasks
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.icalendar.recurrenceId
import at.bitfire.synctools.mapping.tasks.builder.AlarmsBuilder
import at.bitfire.synctools.mapping.tasks.builder.AllDayBuilder
import at.bitfire.synctools.mapping.tasks.builder.CategoriesBuilder
import at.bitfire.synctools.mapping.tasks.builder.ClassificationBuilder
import at.bitfire.synctools.mapping.tasks.builder.ColorBuilder
import at.bitfire.synctools.mapping.tasks.builder.CommentsBuilder
import at.bitfire.synctools.mapping.tasks.builder.CompletedBuilder
import at.bitfire.synctools.mapping.tasks.builder.CreatedBuilder
import at.bitfire.synctools.mapping.tasks.builder.DescriptionBuilder
import at.bitfire.synctools.mapping.tasks.builder.DirtyBuilder
import at.bitfire.synctools.mapping.tasks.builder.DmfsTaskFieldBuilder
import at.bitfire.synctools.mapping.tasks.builder.DmfsTaskFieldBuilderVToDo
import at.bitfire.synctools.mapping.tasks.builder.DueBuilder
import at.bitfire.synctools.mapping.tasks.builder.DurationBuilder
import at.bitfire.synctools.mapping.tasks.builder.ETagBuilder
import at.bitfire.synctools.mapping.tasks.builder.GeoBuilder
import at.bitfire.synctools.mapping.tasks.builder.LastModifiedBuilder
import at.bitfire.synctools.mapping.tasks.builder.ListIdBuilder
import at.bitfire.synctools.mapping.tasks.builder.LocationBuilder
import at.bitfire.synctools.mapping.tasks.builder.OrganizerBuilder
import at.bitfire.synctools.mapping.tasks.builder.PercentCompleteBuilder
import at.bitfire.synctools.mapping.tasks.builder.PriorityBuilder
import at.bitfire.synctools.mapping.tasks.builder.RecurrenceFieldsBuilder
import at.bitfire.synctools.mapping.tasks.builder.RelationsBuilder
import at.bitfire.synctools.mapping.tasks.builder.SequenceBuilder
import at.bitfire.synctools.mapping.tasks.builder.StartTimeBuilder
import at.bitfire.synctools.mapping.tasks.builder.StatusBuilder
import at.bitfire.synctools.mapping.tasks.builder.SyncFlagsBuilder
import at.bitfire.synctools.mapping.tasks.builder.SyncIdBuilder
import at.bitfire.synctools.mapping.tasks.builder.TitleBuilder
import at.bitfire.synctools.mapping.tasks.builder.UidBuilder
import at.bitfire.synctools.mapping.tasks.builder.UnknownPropertiesBuilder
import at.bitfire.synctools.mapping.tasks.builder.UrlBuilder
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.TaskAndExceptions
import at.bitfire.synctools.storage.tasks.TasksBatchOperation
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Writes [at.bitfire.ical4android.Task] to dmfs task provider data rows.
 */
class DmfsTaskBuilder(
    private val taskList: DmfsTaskList,

    @Deprecated("Argument will be removed when we switch to using the new storage/mapping API")
    private val task: Task,

    // DmfsTask-level fields
    private val id: Long?,
    syncId: String?,
    eTag: String?,
    flags: Int
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val fieldBuilders: Array<DmfsTaskFieldBuilderVToDo> = arrayOf(
        // main task row fields
        UidBuilder(),
        SyncIdBuilder(syncId),
        ETagBuilder(eTag),
        SyncFlagsBuilder(flags),
        SequenceBuilder(),
        ListIdBuilder(taskList.id),
        DirtyBuilder(),
        CreatedBuilder(),
        LastModifiedBuilder(),
        // content fields
        TitleBuilder(),
        DescriptionBuilder(),
        LocationBuilder(),
        GeoBuilder(),
        ColorBuilder(),
        UrlBuilder(),
        OrganizerBuilder(),
        // status fields
        PriorityBuilder(),
        ClassificationBuilder(),
        StatusBuilder(),
        CompletedBuilder(),
        PercentCompleteBuilder(),
        // time fields
        AllDayBuilder(),
        StartTimeBuilder(),
        DueBuilder(),
        DurationBuilder(),
        // recurrence
        RecurrenceFieldsBuilder(),
        // property sub-rows
        AlarmsBuilder(taskList),
        CategoriesBuilder(taskList),
        CommentsBuilder(taskList),
        RelationsBuilder(taskList),
        UnknownPropertiesBuilder(taskList),
    )

    @Deprecated("Use at.bitfire.synctools.storage.tasks API instead")
    fun addRows(batch: TasksBatchOperation) {
        val entity = buildTask()

        val mainBuilder = CpoBuilder.newInsert(taskList.tasksUri())
            .withValues(entity.entityValues)
        val idxTask = batch.nextBackrefIdx() // Get nextBackrefIdx BEFORE adding builder to batch
        batch += mainBuilder

        for (subValue in entity.subValues)
            batch += CpoBuilder.newInsert(subValue.uri)
                .withValues(subValue.values)
                .withValueBackReference(Properties.TASK_ID, idxTask)

        logger.log(Level.FINE, "Added task", mainBuilder.build())
    }

    @Deprecated("Use at.bitfire.synctools.storage.tasks API instead")
    fun updateRows(batch: TasksBatchOperation) {
        val existingId = requireNotNull(id)
        val entity = buildTask()

        val mainValues = ContentValues(entity.entityValues).apply {
            // LIST_ID must not be updated (it doesn't change for updates, and setting it would cause issues)
            remove(Tasks.LIST_ID)
        }
        val mainBuilder = CpoBuilder.newUpdate(taskList.taskUri(existingId))
            .withValues(mainValues)
        batch += mainBuilder

        for (subValue in entity.subValues)
            batch += CpoBuilder.newInsert(subValue.uri)
                .withValues(ContentValues(subValue.values).apply {
                    put(Properties.TASK_ID, existingId)
                })

        logger.log(Level.FINE, "Updated task", mainBuilder.build())
    }

    fun build(associatedTasks: AssociatedTasks): TaskAndExceptions {
        val mainVToDo = associatedTasks.main ?: createMainFromExceptions(associatedTasks.exceptions)
        return TaskAndExceptions(
            main = buildTask(from = mainVToDo, main = mainVToDo),
            exceptions = associatedTasks.exceptions.map { exception ->
                buildTask(from = exception, main = mainVToDo)
            }
        )
    }

    private fun buildTask(from: VToDo, main: VToDo): Entity {
        val entity = Entity(ContentValues())
        for (builder in fieldBuilders)
            builder.build(from = from, main = main, to = entity)
        return entity
    }

    @Deprecated("Replaced by build()")
    private fun buildTask(): Entity {
        val entity = Entity(ContentValues())

        for (fieldBuilder in fieldBuilders.filterIsInstance<DmfsTaskFieldBuilder>())
            fieldBuilder.build(task, entity)
        logger.log(Level.FINE, "Built task", entity.entityValues)

        return entity
    }

    /**
     * It is possible that a user receives only exceptions of a task, but not the main task itself.
     * This happens when there's a recurring task that is not visible for the user, but the user is invited to
     * a single recurrence. However, we always need a main task for Android, so we make up one from the
     * exceptions.
     */
    private fun createMainFromExceptions(exceptions: List<VToDo>): VToDo {
        // Should in the future be replaced by a real task that has a title like "(unknown task)".
        // This main task should also have a special extended property that indicates that the task
        // must not actually be generated as main VToDo when the task is locally edited and then uploaded.

        // We need at least one exception to derive a synthetic main task.
        val firstException = exceptions.firstOrNull() ?: return VToDo()

        // Clone the first exception into a new object and drop RECURRENCE-ID so it behaves as a main item.
        val main = VToDo(
            PropertyList(
                firstException.propertyList.all.filterNot { it.name == Property.RECURRENCE_ID }
            ),
            ComponentList(firstException.alarms)
        )

        // If the copied task has no RRULE/RDATE, synthesize recurrence from exception RECURRENCE-IDs.
        val isRecurring = main.getProperty<RRule<*>>(Property.RRULE).isPresent || main.getProperty<RDate<*>>(Property.RDATE).isPresent
        if (!isRecurring) {
            val recurrenceDates = exceptions.mapNotNull { exception ->
                // Use normalizedDate instead of getDate to handle unknown TZIDs
                val recurrenceDate = exception.recurrenceId?.normalizedDate() ?: return@mapNotNull null
                // AndroidTimeUtils expects UTC date-times as OffsetDateTime, not Instant.
                if (recurrenceDate is Instant) {
                    OffsetDateTime.ofInstant(recurrenceDate, ZoneOffset.UTC)
                } else {
                    recurrenceDate
                }
            }
            if (recurrenceDates.isNotEmpty()) {
                main += RDate(DateList(*recurrenceDates.toTypedArray()))
            }
        }

        return main
    }

}
