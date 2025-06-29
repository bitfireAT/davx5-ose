/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentUris
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.db.SyncState
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.CalendarBatchOperation
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
        get() = androidCalendar.accessLevel?.let { it <= Calendars.CAL_ACCESS_READ } ?: false

    override var lastSyncState: SyncState?
        get() = androidCalendar.readSyncState()?.let { SyncState.fromString(it) }
        set(state) {
            androidCalendar.writeSyncState(state.toString())
        }


    override fun findDeleted() =
        androidCalendar.queryEvents("${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NULL", null)
            .map { LocalEvent(it) }

    override fun findDirty(): List<LocalEvent> {
        val dirty = LinkedList<LocalEvent>()

        /*
         * RFC 5545 3.8.7.4. Sequence Number
         * When a calendar component is created, its sequence number is 0. It is monotonically incremented by the "Organizer's"
         * CUA each time the "Organizer" makes a significant revision to the calendar component.
         */
        for (androidEvent in androidCalendar.queryEvents("${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL", null)) {
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
        androidCalendar.queryEvents("${Events._SYNC_ID}=?", arrayOf(name)).firstOrNull()?.let { LocalEvent(it) }


    override fun markNotDirty(flags: Int): Int {
        val values = contentValuesOf(AndroidEvent.COLUMN_FLAGS to flags)
        return androidCalendar.provider.update(
            Events.CONTENT_URI.asSyncAdapter(androidCalendar.account), values,
                "${Events.CALENDAR_ID}=? AND NOT ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL",
            arrayOf(androidCalendar.id.toString())
        )
    }

    override fun removeNotDirtyMarked(flags: Int): Int {
        var deleted = 0
        // list all non-dirty events with the given flags and delete every row + its exceptions
        androidCalendar.provider.query(
            Events.CONTENT_URI.asSyncAdapter(androidCalendar.account), arrayOf(Events._ID),
            "${Events.CALENDAR_ID}=? AND NOT ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL AND ${AndroidEvent.COLUMN_FLAGS}=?",
            arrayOf(androidCalendar.id.toString(), flags.toString()), null
        )?.use { cursor ->
            val batch = CalendarBatchOperation(androidCalendar.provider)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                // delete event and possible exceptions (content provider doesn't delete exceptions itself)
                batch += BatchOperation.CpoBuilder
                    .newDelete(Events.CONTENT_URI.asSyncAdapter(androidCalendar.account))
                    .withSelection("${Events._ID}=? OR ${Events.ORIGINAL_ID}=?", arrayOf(id.toString(), id.toString()))
            }
            deleted = batch.commit()
        }
        return deleted
    }

    override fun forgetETags() {
        val values = contentValuesOf(AndroidEvent.COLUMN_ETAG to null)
        androidCalendar.provider.update(
            Events.CONTENT_URI.asSyncAdapter(androidCalendar.account), values, "${Events.CALENDAR_ID}=?",
            arrayOf(androidCalendar.id.toString())
        )
    }


    fun processDirtyExceptions() {
        // process deleted exceptions
        logger.info("Processing deleted exceptions")
        androidCalendar.provider.query(
            Events.CONTENT_URI.asSyncAdapter(androidCalendar.account),
            arrayOf(Events._ID, Events.ORIGINAL_ID, AndroidEvent.COLUMN_SEQUENCE),
                "${Events.CALENDAR_ID}=? AND ${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NOT NULL",
            arrayOf(androidCalendar.id.toString()), null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                logger.fine("Found deleted exception, removing and re-scheduling original event (if available)")
                val id = cursor.getLong(0)             // can't be null (by definition)
                val originalID = cursor.getLong(1)     // can't be null (by query)

                val batch = CalendarBatchOperation(androidCalendar.provider)

                // get original event's SEQUENCE
                androidCalendar.provider.query(
                    ContentUris.withAppendedId(Events.CONTENT_URI, originalID).asSyncAdapter(androidCalendar.account),
                    arrayOf(AndroidEvent.COLUMN_SEQUENCE),
                        null, null, null)?.use { cursor2 ->
                    if (cursor2.moveToNext()) {
                        // original event is available
                        val originalSequence = if (cursor2.isNull(0)) 0 else cursor2.getInt(0)

                        // re-schedule original event and set it to DIRTY
                        batch += BatchOperation.CpoBuilder
                            .newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, originalID).asSyncAdapter(androidCalendar.account))
                            .withValue(AndroidEvent.COLUMN_SEQUENCE, originalSequence + 1)
                            .withValue(Events.DIRTY, 1)
                    }
                }

                // completely remove deleted exception
                batch += BatchOperation.CpoBuilder.newDelete(ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(androidCalendar.account))
                batch.commit()
            }
        }

        // process dirty exceptions
        logger.info("Processing dirty exceptions")
        androidCalendar.provider.query(
            Events.CONTENT_URI.asSyncAdapter(androidCalendar.account),
            arrayOf(Events._ID, Events.ORIGINAL_ID, AndroidEvent.COLUMN_SEQUENCE),
                "${Events.CALENDAR_ID}=? AND ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NOT NULL",
            arrayOf(androidCalendar.id.toString()), null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                logger.fine("Found dirty exception, increasing SEQUENCE to re-schedule")
                val id = cursor.getLong(0)             // can't be null (by definition)
                val originalID = cursor.getLong(1)     // can't be null (by query)
                val sequence = if (cursor.isNull(2)) 0 else cursor.getInt(2)

                val batch = CalendarBatchOperation(androidCalendar.provider)
                // original event to DIRTY
                batch += BatchOperation.CpoBuilder
                    .newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, originalID).asSyncAdapter(androidCalendar.account))
                    .withValue(Events.DIRTY, 1)
                // increase SEQUENCE and set DIRTY to 0
                batch += BatchOperation.CpoBuilder
                    .newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(androidCalendar.account))
                    .withValue(AndroidEvent.COLUMN_SEQUENCE, sequence + 1)
                    .withValue(Events.DIRTY, 0)
                batch.commit()
            }
        }
    }

    /**
     * Marks dirty events (which are not already marked as deleted) which got no valid instances as "deleted"
     *
     * @return number of affected events
     */
    fun deleteDirtyEventsWithoutInstances() {
        androidCalendar.provider.query(
            Events.CONTENT_URI.asSyncAdapter(androidCalendar.account),
            arrayOf(Events._ID),
            "${Events.DIRTY} AND NOT ${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NULL",    // Get dirty main events (and no exception events)
            null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val eventID = cursor.getLong(0)

                // get number of instances
                val numEventInstances = AndroidEvent.numInstances(androidCalendar.provider, androidCalendar.account, eventID)

                // delete event if there are no instances
                if (numEventInstances == 0) {
                    logger.info("Marking event #$eventID without instances as deleted")
                    AndroidEvent.markAsDeleted(androidCalendar.provider, androidCalendar.account, eventID)
                }
            }
        }
    }

}