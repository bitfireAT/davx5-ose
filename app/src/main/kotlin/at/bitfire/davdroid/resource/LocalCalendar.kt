/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.db.SyncState
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidCalendarFactory
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
class LocalCalendar private constructor(
    account: Account,
    provider: ContentProviderClient,
    id: Long
): AndroidCalendar<LocalEvent>(account, provider, LocalEvent.Factory, id), LocalCollection<LocalEvent> {

    companion object {

        private const val COLUMN_SYNC_STATE = Calendars.CAL_SYNC1

        private val logger: Logger
            get() = Logger.getGlobal()

    }

    override val dbCollectionId: Long?
        get() = syncId?.toLongOrNull()

    override val tag: String
        get() = "events-${account.name}-$id"

    override val title: String
        get() = displayName ?: id.toString()

    override val readOnly
        get() = accessLevel?.let { it <= Calendars.CAL_ACCESS_READ } ?: false

    override var lastSyncState: SyncState?
        get() = provider.query(calendarSyncURI(), arrayOf(COLUMN_SYNC_STATE), null, null, null)?.use { cursor ->
                    if (cursor.moveToNext())
                        return SyncState.fromString(cursor.getString(0))
                    else
                        null
                }
        set(state) {
            val values = contentValuesOf(COLUMN_SYNC_STATE to state.toString())
            provider.update(calendarSyncURI(), values, null, null)
        }


    override fun findDeleted() =
        queryEvents("${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NULL", null)

    override fun findDirty(): List<LocalEvent> {
        val dirty = LinkedList<LocalEvent>()

        /*
         * RFC 5545 3.8.7.4. Sequence Number
         * When a calendar component is created, its sequence number is 0. It is monotonically incremented by the "Organizer's"
         * CUA each time the "Organizer" makes a significant revision to the calendar component.
         */
        for (localEvent in queryEvents("${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL", null)) {
            try {
                val event = requireNotNull(localEvent.event)

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
            queryEvents("${Events._SYNC_ID}=?", arrayOf(name)).firstOrNull()


    override fun markNotDirty(flags: Int): Int {
        val values = contentValuesOf(AndroidEvent.COLUMN_FLAGS to flags)
        return provider.update(Events.CONTENT_URI.asSyncAdapter(account), values,
                "${Events.CALENDAR_ID}=? AND NOT ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL",
                arrayOf(id.toString()))
    }

    override fun removeNotDirtyMarked(flags: Int): Int {
        var deleted = 0
        // list all non-dirty events with the given flags and delete every row + its exceptions
        provider.query(Events.CONTENT_URI.asSyncAdapter(account), arrayOf(Events._ID),
            "${Events.CALENDAR_ID}=? AND NOT ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL AND ${AndroidEvent.COLUMN_FLAGS}=?",
                arrayOf(id.toString(), flags.toString()), null)?.use { cursor ->
            val batch = CalendarBatchOperation(provider)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                // delete event and possible exceptions (content provider doesn't delete exceptions itself)
                batch += BatchOperation.CpoBuilder
                    .newDelete(Events.CONTENT_URI.asSyncAdapter(account))
                    .withSelection("${Events._ID}=? OR ${Events.ORIGINAL_ID}=?", arrayOf(id.toString(), id.toString()))
            }
            deleted = batch.commit()
        }
        return deleted
    }

    override fun forgetETags() {
        val values = contentValuesOf(AndroidEvent.COLUMN_ETAG to null)
        provider.update(Events.CONTENT_URI.asSyncAdapter(account), values, "${Events.CALENDAR_ID}=?",
                arrayOf(id.toString()))
    }


    fun processDirtyExceptions() {
        // process deleted exceptions
        logger.info("Processing deleted exceptions")
        provider.query(
                Events.CONTENT_URI.asSyncAdapter(account),
            arrayOf(Events._ID, Events.ORIGINAL_ID, AndroidEvent.COLUMN_SEQUENCE),
                "${Events.CALENDAR_ID}=? AND ${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NOT NULL",
                arrayOf(id.toString()), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                logger.fine("Found deleted exception, removing and re-scheduling original event (if available)")
                val id = cursor.getLong(0)             // can't be null (by definition)
                val originalID = cursor.getLong(1)     // can't be null (by query)

                val batch = CalendarBatchOperation(provider)

                // get original event's SEQUENCE
                provider.query(
                        ContentUris.withAppendedId(Events.CONTENT_URI, originalID).asSyncAdapter(account),
                    arrayOf(AndroidEvent.COLUMN_SEQUENCE),
                        null, null, null)?.use { cursor2 ->
                    if (cursor2.moveToNext()) {
                        // original event is available
                        val originalSequence = if (cursor2.isNull(0)) 0 else cursor2.getInt(0)

                        // re-schedule original event and set it to DIRTY
                        batch += BatchOperation.CpoBuilder
                            .newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, originalID).asSyncAdapter(account))
                            .withValue(AndroidEvent.COLUMN_SEQUENCE, originalSequence + 1)
                            .withValue(Events.DIRTY, 1)
                    }
                }

                // completely remove deleted exception
                batch += BatchOperation.CpoBuilder.newDelete(ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(account))
                batch.commit()
            }
        }

        // process dirty exceptions
        logger.info("Processing dirty exceptions")
        provider.query(
                Events.CONTENT_URI.asSyncAdapter(account),
            arrayOf(Events._ID, Events.ORIGINAL_ID, AndroidEvent.COLUMN_SEQUENCE),
                "${Events.CALENDAR_ID}=? AND ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NOT NULL",
                arrayOf(id.toString()), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                logger.fine("Found dirty exception, increasing SEQUENCE to re-schedule")
                val id = cursor.getLong(0)             // can't be null (by definition)
                val originalID = cursor.getLong(1)     // can't be null (by query)
                val sequence = if (cursor.isNull(2)) 0 else cursor.getInt(2)

                val batch = CalendarBatchOperation(provider)
                // original event to DIRTY
                batch += BatchOperation.CpoBuilder
                    .newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, originalID).asSyncAdapter(account))
                    .withValue(Events.DIRTY, 1)
                // increase SEQUENCE and set DIRTY to 0
                batch += BatchOperation.CpoBuilder
                    .newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(account))
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
        provider.query(
            Events.CONTENT_URI.asSyncAdapter(account),
            arrayOf(Events._ID),
            "${Events.DIRTY} AND NOT ${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NULL",    // Get dirty main events (and no exception events)
            null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val eventID = cursor.getLong(0)

                // get number of instances
                val numEventInstances = LocalEvent.numInstances(provider, account, eventID)

                // delete event if there are no instances
                if (numEventInstances == 0) {
                    logger.info("Marking event #$eventID without instances as deleted")
                    LocalEvent.markAsDeleted(provider, account, eventID)
                }
            }
        }
    }


    object Factory: AndroidCalendarFactory<LocalCalendar> {

        override fun newInstance(account: Account, provider: ContentProviderClient, id: Long) =
            LocalCalendar(account, provider, id)

    }

}