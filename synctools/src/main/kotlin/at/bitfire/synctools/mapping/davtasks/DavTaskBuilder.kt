/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.exception.ResourceMappingException
import at.bitfire.synctools.icalendar.AssociatedTasks
import at.bitfire.synctools.mapping.davtasks.builder.AllDayBuilder
import at.bitfire.synctools.mapping.davtasks.builder.AlarmsBuilder
import at.bitfire.synctools.mapping.davtasks.builder.AttachmentsBuilder
import at.bitfire.synctools.mapping.davtasks.builder.AttendeesBuilder
import at.bitfire.synctools.mapping.davtasks.builder.CategoriesBuilder
import at.bitfire.synctools.mapping.davtasks.builder.ClassificationBuilder
import at.bitfire.synctools.mapping.davtasks.builder.ColorBuilder
import at.bitfire.synctools.mapping.davtasks.builder.CommentsBuilder
import at.bitfire.synctools.mapping.davtasks.builder.CompletedBuilder
import at.bitfire.synctools.mapping.davtasks.builder.ContactBuilder
import at.bitfire.synctools.mapping.davtasks.builder.CreatedBuilder
import at.bitfire.synctools.mapping.davtasks.builder.DavTaskEntityBuilder
import at.bitfire.synctools.mapping.davtasks.builder.DescriptionBuilder
import at.bitfire.synctools.mapping.davtasks.builder.DirtyBuilder
import at.bitfire.synctools.mapping.davtasks.builder.DueBuilder
import at.bitfire.synctools.mapping.davtasks.builder.DurationBuilder
import at.bitfire.synctools.mapping.davtasks.builder.ETagBuilder
import at.bitfire.synctools.mapping.davtasks.builder.GeoBuilder
import at.bitfire.synctools.mapping.davtasks.builder.LastModifiedBuilder
import at.bitfire.synctools.mapping.davtasks.builder.ListIdBuilder
import at.bitfire.synctools.mapping.davtasks.builder.LocationBuilder
import at.bitfire.synctools.mapping.davtasks.builder.OrganizerBuilder
import at.bitfire.synctools.mapping.davtasks.builder.PercentCompleteBuilder
import at.bitfire.synctools.mapping.davtasks.builder.PriorityBuilder
import at.bitfire.synctools.mapping.davtasks.builder.RecurrenceFieldsBuilder
import at.bitfire.synctools.mapping.davtasks.builder.RelationsBuilder
import at.bitfire.synctools.mapping.davtasks.builder.RequestStatusBuilder
import at.bitfire.synctools.mapping.davtasks.builder.ResourcesBuilder
import at.bitfire.synctools.mapping.davtasks.builder.SequenceBuilder
import at.bitfire.synctools.mapping.davtasks.builder.StartTimeBuilder
import at.bitfire.synctools.mapping.davtasks.builder.StatusBuilder
import at.bitfire.synctools.mapping.davtasks.builder.SummaryBuilder
import at.bitfire.synctools.mapping.davtasks.builder.SyncFlagsBuilder
import at.bitfire.synctools.mapping.davtasks.builder.SyncIdBuilder
import at.bitfire.synctools.mapping.davtasks.builder.UidBuilder
import at.bitfire.synctools.mapping.davtasks.builder.UnknownPropertiesBuilder
import at.bitfire.synctools.mapping.davtasks.builder.UrlBuilder
import at.bitfire.synctools.storage.davtasks.DavTaskAndExceptions
import at.bitfire.synctools.storage.davtasks.DavTaskList
import net.fortuna.ical4j.model.component.VToDo
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Maps an iCal4j [VToDo] to an [Entity] for storage in the DAVx⁵-hosted tasks provider.
 * Structurally mirrors [at.bitfire.synctools.mapping.tasks.DmfsTaskBuilder] (§5 of the design doc).
 */
class DavTaskBuilder(
    taskList: DavTaskList,

    syncId: String?,
    eTag: String?,
    flags: Int
) {

    private val allDayBuilder = AllDayBuilder()

    private val entityBuilders: Array<DavTaskEntityBuilder> = arrayOf(
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
        SummaryBuilder(),
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
        // time fields (AllDayBuilder must run before Start/Due/Recurrence, they read IS_ALLDAY back)
        allDayBuilder,
        StartTimeBuilder(allDayBuilder),
        DueBuilder(allDayBuilder),
        DurationBuilder(),
        // recurrence
        RecurrenceFieldsBuilder(allDayBuilder),
        // sub-rows: Properties
        CategoriesBuilder(taskList),
        CommentsBuilder(taskList),
        RelationsBuilder(taskList),
        AttendeesBuilder(taskList),
        AttachmentsBuilder(taskList),
        ContactBuilder(taskList),
        ResourcesBuilder(taskList),
        RequestStatusBuilder(taskList),
        UnknownPropertiesBuilder(taskList),
        // sub-rows: Alarms
        AlarmsBuilder(taskList),
    )

    private val logger
        get() = Logger.getLogger(javaClass.name)

    fun build(associatedTasks: AssociatedTasks): DavTaskAndExceptions {
        val mainVToDo = associatedTasks.main
            ?: throw ResourceMappingException("Main task is missing in associated tasks")

        /* Recurring task exceptions are not yet supported (same limitation as the DMFS backend,
        #2357) — drop them rather than write them without ORIGINAL_INSTANCE_TIME. */
        if (associatedTasks.exceptions.isNotEmpty())
            logger.log(
                Level.WARNING,
                "Ignoring {0} exception(s) of recurring task (not yet supported, see #2357)",
                arrayOf(associatedTasks.exceptions.size)
            )

        return DavTaskAndExceptions(
            main = buildTask(from = mainVToDo, main = mainVToDo),
            exceptions = emptyList()
        )
    }

    private fun buildTask(from: VToDo, main: VToDo): Entity {
        val entity = Entity(ContentValues())
        for (builder in entityBuilders)
            builder.build(from = from, main = main, to = entity)
        return entity
    }

}
