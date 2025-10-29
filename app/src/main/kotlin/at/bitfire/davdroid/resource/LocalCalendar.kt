/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentUris
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidRecurringCalendar
import at.bitfire.synctools.storage.calendar.CalendarBatchOperation
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import at.bitfire.synctools.storage.calendar.EventsContract
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.LinkedList
import java.util.logging.Logger

/**
 * Application-specific subclass of [AndroidCalendar] for local calendars.
 *
 * [Calendars._SYNC_ID] corresponds to the database collection ID ([at.bitfire.davdroid.db.Collection.id]).
 */
class LocalCalendar @AssistedInject constructor(
    @Assisted internal val androidCalendar: AndroidCalendar,
    private val logger: Logger
) : LocalCollection<LocalEvent> {

    @AssistedFactory
    interface Factory {
        fun create(calendar: AndroidCalendar): LocalCalendar
    }


    // properties

    override val dbCollectionId: Long?
        get() = androidCalendar.syncId?.toLongOrNull()

    override val tag: String
        get() = "events-${androidCalendar.account.name}-${androidCalendar.id}"

    override val title: String
        get() = androidCalendar.displayName ?: androidCalendar.id.toString()

    override val readOnly
        get() = androidCalendar.accessLevel <= Calendars.CAL_ACCESS_READ

    override var lastSyncState: SyncState?
        get() = androidCalendar.readSyncState()?.let {
            SyncState.fromString(it)
        }
        set(state) {
            androidCalendar.writeSyncState(state.toString())
        }

    private val recurringCalendar = AndroidRecurringCalendar(androidCalendar)


    fun add(event: EventAndExceptions) {
        recurringCalendar.addEventAndExceptions(event)
    }

    override fun findDeleted(): List<LocalEvent> {
        val result = LinkedList<LocalEvent>()
        // TODO null whereArgs
        recurringCalendar.iterateEventAndExceptions(
            "${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NULL",
            emptyArray()
        ) { eventAndExceptions ->
            result += LocalEvent(recurringCalendar, eventAndExceptions)
        }
        return result
    }

    override fun findDirty(): List<LocalEvent> {
        val dirty = LinkedList<LocalEvent>()

        // TODO SEQUENCE
        /*
         * RFC 5545 3.8.7.4. Sequence Number
         * When a calendar component is created, its sequence number is 0. It is monotonically incremented by the "Organizer's"
         * CUA each time the "Organizer" makes a significant revision to the calendar component.
         */

        // TODO null whereArgs
        recurringCalendar.iterateEventAndExceptions(
            "${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL",
            emptyArray()
        ) { eventAndExceptions ->
            dirty += LocalEvent(recurringCalendar, eventAndExceptions)
        }
        return dirty
    }

    override fun findByName(name: String) =
        androidCalendar.findEventRow(arrayOf(Events._ID), "${Events._SYNC_ID}=? AND ${Events.ORIGINAL_SYNC_ID} IS null", arrayOf(name))?.let { values ->
            val id = values.getAsLong(Events._ID)
            val eventAndExceptions = recurringCalendar.getById(id)!!
            LocalEvent(recurringCalendar, eventAndExceptions)
        }

    override fun markNotDirty(flags: Int) =
        androidCalendar.updateEventRows(
            contentValuesOf(EventsContract.COLUMN_FLAGS to flags),
            // `dirty` can be 0, 1, or null. "NOT dirty" is not enough.
            """
                ${Events.CALENDAR_ID}=?
                AND (${Events.DIRTY} IS NULL OR ${Events.DIRTY}=0)
                AND ${Events.ORIGINAL_ID} IS NULL
            """.trimIndent(),
            arrayOf(androidCalendar.id.toString())
        )

    override fun removeNotDirtyMarked(flags: Int): Int {
        // list all non-dirty events with the given flags and delete every row + its exceptions
        val batch = CalendarBatchOperation(androidCalendar.client)
        androidCalendar.iterateEventRows(
            arrayOf(Events._ID),
            // `dirty` can be 0, 1, or null. "NOT dirty" is not enough.
            """
                ${Events.CALENDAR_ID}=?
                AND (${Events.DIRTY} IS NULL OR ${Events.DIRTY}=0)
                AND ${Events.ORIGINAL_ID} IS NULL
                AND ${EventsContract.COLUMN_FLAGS}=?
            """.trimIndent(),
            arrayOf(androidCalendar.id.toString(), flags.toString())
        ) { values ->
            val id = values.getAsLong(Events._ID)

            // delete event and possible exceptions (content provider doesn't delete exceptions itself)
            batch += BatchOperation.CpoBuilder
                .newDelete(androidCalendar.eventsUri)
                .withSelection("${Events._ID}=? OR ${Events.ORIGINAL_ID}=?", arrayOf(id.toString(), id.toString()))
        }
        return batch.commit()
    }

    override fun forgetETags() {
        androidCalendar.updateEventRows(
            contentValuesOf(EventsContract.COLUMN_ETAG to null),
            "${Events.CALENDAR_ID}=?", arrayOf(androidCalendar.id.toString())
        )
    }


    fun processDirtyExceptions() {
        // process deleted exceptions
        logger.info("Processing deleted exceptions")

        androidCalendar.iterateEventRows(
            arrayOf(Events._ID, Events.ORIGINAL_ID, EventsContract.COLUMN_SEQUENCE),
            "${Events.CALENDAR_ID}=? AND ${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NOT NULL",
            arrayOf(androidCalendar.id.toString())
        ) { values ->
            logger.fine("Found deleted exception, removing and re-scheduling original event (if available)")

            val id = values.getAsLong(Events._ID)                   // can't be null (by definition)
            val originalID = values.getAsLong(Events.ORIGINAL_ID)   // can't be null (by query)

            val batch = CalendarBatchOperation(androidCalendar.client)

            // enqueue: increase sequence of main event
            val originalEventValues = androidCalendar.getEventRow(originalID, arrayOf(EventsContract.COLUMN_SEQUENCE))
            val originalSequence = originalEventValues?.getAsInteger(EventsContract.COLUMN_SEQUENCE) ?: 0

            batch += BatchOperation.CpoBuilder
                .newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, originalID).asSyncAdapter(androidCalendar.account))
                .withValue(EventsContract.COLUMN_SEQUENCE, originalSequence + 1)
                .withValue(Events.DIRTY, 1)

            // completely remove deleted exception
            batch += BatchOperation.CpoBuilder.newDelete(ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(androidCalendar.account))
            batch.commit()
        }

        // process dirty exceptions
        logger.info("Processing dirty exceptions")
        androidCalendar.iterateEventRows(
            arrayOf(Events._ID, Events.ORIGINAL_ID, EventsContract.COLUMN_SEQUENCE),
            "${Events.CALENDAR_ID}=? AND ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NOT NULL",
            arrayOf(androidCalendar.id.toString())
        ) { values ->
            logger.fine("Found dirty exception, increasing SEQUENCE to re-schedule")

            val id = values.getAsLong(Events._ID)                   // can't be null (by definition)
            val originalID = values.getAsLong(Events.ORIGINAL_ID)   // can't be null (by query)
            val sequence = values.getAsInteger(EventsContract.COLUMN_SEQUENCE) ?: 0

            val batch = CalendarBatchOperation(androidCalendar.client)

            // enqueue: set original event to DIRTY
            batch += BatchOperation.CpoBuilder
                .newUpdate(androidCalendar.eventUri(originalID))
                .withValue(Events.DIRTY, 1)

            // enqueue: increase exception SEQUENCE and set DIRTY to 0
            batch += BatchOperation.CpoBuilder
                .newUpdate(androidCalendar.eventUri(id))
                .withValue(EventsContract.COLUMN_SEQUENCE, sequence + 1)
                .withValue(Events.DIRTY, 0)

            batch.commit()
        }
    }

    /**
     * Marks dirty events (which are not already marked as deleted) which got no valid instances as "deleted"
     *
     * @return number of affected events
     */
    fun deleteDirtyEventsWithoutInstances() {
        // Iterate dirty main events without exceptions
        androidCalendar.iterateEventRows(
            arrayOf(Events._ID),
            "${Events.DIRTY} AND NOT ${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NULL",
            null
        ) { values ->
            val eventId = values.getAsLong(Events._ID)

            // get number of instances
            val numEventInstances = androidCalendar.numInstances(eventId)

            // delete event if there are no instances
            if (numEventInstances == 0) {
                logger.fine("Marking event #$eventId without instances as deleted")
                androidCalendar.updateEventRow(eventId, contentValuesOf(Events.DELETED to 1))
            }
        }
    }

}