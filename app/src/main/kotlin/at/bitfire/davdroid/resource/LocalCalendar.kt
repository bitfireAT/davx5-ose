/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentUris
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.db.SyncState
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.CalendarBatchOperation
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Application-specific subclass of [AndroidCalendar] for local calendars.
 *
 * [Calendars._SYNC_ID] corresponds to the database collection ID ([at.bitfire.davdroid.db.Collection.id]).
 */
class LocalCalendar(
    val androidCalendar: AndroidCalendar
) : LocalCollection<LocalEvent> {

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)


    override val dbCollectionId: Long?
        get() = androidCalendar.syncId?.toLongOrNull()

    override val tag: String
        get() = "events-${androidCalendar.account.name}-${androidCalendar.id}"

    override val title: String
        get() = androidCalendar.displayName ?: androidCalendar.id.toString()

    override val readOnly
        get() = androidCalendar.accessLevel <= Calendars.CAL_ACCESS_READ

    override var lastSyncState: SyncState?
        get() = androidCalendar.readSyncState(androidCalendar.id)?.let {
            SyncState.fromString(it)
        }
        set(state) {
            androidCalendar.writeSyncState(androidCalendar.id, state.toString())
        }


    override fun findDeleted() =
        androidCalendar
            .findEvents("${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NULL", null)
            .map { LocalEvent(it) }

    override fun findDirty(): List<LocalEvent> {
        val dirty = LinkedList<LocalEvent>()

        /*
         * RFC 5545 3.8.7.4. Sequence Number
         * When a calendar component is created, its sequence number is 0. It is monotonically incremented by the "Organizer's"
         * CUA each time the "Organizer" makes a significant revision to the calendar component.
         */
        for (androidEvent in androidCalendar.findEvents("${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL", null)) {
            val localEvent = LocalEvent(androidEvent)
            try {
                val event = requireNotNull(androidEvent.event)

                val nonGroupScheduled = event.attendees.isEmpty()
                val weAreOrganizer = localEvent.weAreOrganizer

                val sequence = event.sequence
                if (sequence == null)
                    // sequence has not been assigned yet (i.e. this event was just locally created)
                    event.sequence = 0
                else if (nonGroupScheduled || weAreOrganizer)   // increase sequence
                    event.sequence = sequence + 1

            } catch(e: Exception) {
                logger.log(Level.WARNING, "Couldn't check/increase sequence", e)
            }
            dirty += localEvent
        }

        return dirty
    }

    override fun findByName(name: String) =
        androidCalendar.findEvents("${Events._SYNC_ID}=?", arrayOf(name)).firstOrNull()?.let { LocalEvent(it) }


    override fun markNotDirty(flags: Int) =
        androidCalendar.updateEvents(
            contentValuesOf(AndroidEvent.COLUMN_FLAGS to flags),
            "${Events.CALENDAR_ID}=? AND NOT ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL",
            arrayOf(androidCalendar.id.toString())
        )

    override fun removeNotDirtyMarked(flags: Int): Int {
        // list all non-dirty events with the given flags and delete every row + its exceptions
        val batch = CalendarBatchOperation(androidCalendar.client)
        androidCalendar.iterateEvents(
            arrayOf(Events._ID),
            "${Events.CALENDAR_ID}=? AND NOT ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL AND ${AndroidEvent.COLUMN_FLAGS}=?",
            arrayOf(androidCalendar.id.toString(), flags.toString())
        ) { values ->
            val id = values.getAsInteger(Events._ID)

            // delete event and possible exceptions (content provider doesn't delete exceptions itself)
            batch += BatchOperation.CpoBuilder
                .newDelete(Events.CONTENT_URI.asSyncAdapter(androidCalendar.account))
                .withSelection("${Events._ID}=? OR ${Events.ORIGINAL_ID}=?", arrayOf(id.toString(), id.toString()))
        }
        return batch.commit()
    }

    override fun forgetETags() {
        androidCalendar.updateEvents(
            contentValuesOf(AndroidEvent.COLUMN_ETAG to null),
            "${Events.CALENDAR_ID}=?", arrayOf(androidCalendar.id.toString())
        )
    }


    fun processDirtyExceptions() {
        // process deleted exceptions
        logger.info("Processing deleted exceptions")

        androidCalendar.iterateEvents(
            arrayOf(Events._ID, Events.ORIGINAL_ID, AndroidEvent.COLUMN_SEQUENCE),
            "${Events.CALENDAR_ID}=? AND ${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NOT NULL",
            arrayOf(androidCalendar.id.toString())
        ) { values ->
            logger.fine("Found deleted exception, removing and re-scheduling original event (if available)")

            val id = values.getAsLong(Events._ID)                   // can't be null (by definition)
            val originalID = values.getAsLong(Events.ORIGINAL_ID)   // can't be null (by query)

            val batch = CalendarBatchOperation(androidCalendar.client)

            // enqueue: increase sequence of main event
            val originalEventValues = androidCalendar.getEventValues(originalID, arrayOf(AndroidEvent.COLUMN_SEQUENCE))
            val originalSequence = originalEventValues?.getAsInteger(AndroidEvent.COLUMN_SEQUENCE) ?: 0

            batch += BatchOperation.CpoBuilder
                .newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, originalID).asSyncAdapter(androidCalendar.account))
                .withValue(AndroidEvent.COLUMN_SEQUENCE, originalSequence + 1)
                .withValue(Events.DIRTY, 1)

            // completely remove deleted exception
            batch += BatchOperation.CpoBuilder.newDelete(ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(androidCalendar.account))
            batch.commit()
        }

        // process dirty exceptions
        logger.info("Processing dirty exceptions")
        androidCalendar.iterateEvents(
            arrayOf(Events._ID, Events.ORIGINAL_ID, AndroidEvent.COLUMN_SEQUENCE),
            "${Events.CALENDAR_ID}=? AND ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NOT NULL",
            arrayOf(androidCalendar.id.toString())
        ) { values ->
            logger.fine("Found dirty exception, increasing SEQUENCE to re-schedule")

            val id = values.getAsLong(Events._ID)                   // can't be null (by definition)
            val originalID = values.getAsLong(Events.ORIGINAL_ID)   // can't be null (by query)
            val sequence = values.getAsInteger(AndroidEvent.COLUMN_SEQUENCE) ?: 0

            val batch = CalendarBatchOperation(androidCalendar.client)

            // enqueue: set original event to DIRTY
            batch += BatchOperation.CpoBuilder
                .newUpdate(androidCalendar.eventUri(originalID))
                .withValue(Events.DIRTY, 1)

            // enqueue: increase exception SEQUENCE and set DIRTY to 0
            batch += BatchOperation.CpoBuilder
                .newUpdate(androidCalendar.eventUri(id))
                .withValue(AndroidEvent.COLUMN_SEQUENCE, sequence + 1)
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
        androidCalendar.iterateEvents(
            arrayOf(Events._ID),
            "${Events.DIRTY} AND NOT ${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NULL",
            null
        ) { values ->
            val eventID = values.getAsLong(Events._ID)

            // get number of instances
            val numEventInstances = AndroidEvent.numInstances(androidCalendar.client, androidCalendar.account, eventID)

            // delete event if there are no instances
            if (numEventInstances == 0) {
                logger.fine("Marking event #$eventID without instances as deleted")
                AndroidEvent.markAsDeleted(androidCalendar.client, androidCalendar.account, eventID)
            }
        }
    }

}