/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.exception.ResourceMappingException
import at.bitfire.synctools.icalendar.AssociatedTasks
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
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.TaskAndExceptions
import net.fortuna.ical4j.model.component.VToDo
import java.util.logging.Logger

/**
 * Writes [at.bitfire.ical4android.Task] to dmfs task provider data rows.
 */
class DmfsTaskBuilder(
    private val taskList: DmfsTaskList,

    // DmfsTask-level fields
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

    fun build(associatedTasks: AssociatedTasks): TaskAndExceptions {
        // TODO: when recurrence is supported on tasks, no exception should be thrown, but instead a reference should be created for exceptions. See AndroidEventBuilder
        val mainVToDo = associatedTasks.main ?: throw ResourceMappingException("Main task is missing in associated tasks")
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

}
