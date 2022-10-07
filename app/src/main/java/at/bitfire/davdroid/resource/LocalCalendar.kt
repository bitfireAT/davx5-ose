/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.log.Logger
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidCalendarFactory
import at.bitfire.ical4android.BatchOperation
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.MiscUtils.UriHelper.asSyncAdapter
import java.util.*
import java.util.logging.Level

class LocalCalendar private constructor(
        account: Account,
        provider: ContentProviderClient,
        id: Long
): AndroidCalendar<LocalEvent>(account, provider, LocalEvent.Factory, id), LocalCollection<LocalEvent> {

    companion object {

        private const val COLUMN_SYNC_STATE = Calendars.CAL_SYNC1

        fun create(account: Account, provider: ContentProviderClient, info: Collection): Uri {
            val values = valuesFromCollectionInfo(info, true)

            // ACCOUNT_NAME and ACCOUNT_TYPE are required (see docs)! If it's missing, other apps will crash.
            values.put(Calendars.ACCOUNT_NAME, account.name)
            values.put(Calendars.ACCOUNT_TYPE, account.type)

            // Email address for scheduling. Used by the calendar provider to determine whether the
            // user is ORGANIZER/ATTENDEE for a certain event.
            values.put(Calendars.OWNER_ACCOUNT, account.name)

            // flag as visible & synchronizable at creation, might be changed by user at any time
            values.put(Calendars.VISIBLE, 1)
            values.put(Calendars.SYNC_EVENTS, 1)
            return create(account, provider, values)
        }

        private fun valuesFromCollectionInfo(info: Collection, withColor: Boolean): ContentValues {
            val values = ContentValues()
            values.put(Calendars.NAME, info.url.toString())
            values.put(Calendars.CALENDAR_DISPLAY_NAME, if (info.displayName.isNullOrBlank()) DavUtils.lastSegmentOfUrl(info.url) else info.displayName)

            if (withColor)
                values.put(Calendars.CALENDAR_COLOR, info.color ?: Constants.DAVDROID_GREEN_RGBA)

            if (info.privWriteContent && !info.forceReadOnly) {
                values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                values.put(Calendars.CAN_MODIFY_TIME_ZONE, 1)
                values.put(Calendars.CAN_ORGANIZER_RESPOND, 1)
            } else
                values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ)

            info.timezone?.let { tzData ->
                try {
                    val timeZone = DateUtils.parseVTimeZone(tzData)
                    timeZone.timeZoneId?.let { tzId ->
                        values.put(Calendars.CALENDAR_TIME_ZONE, DateUtils.findAndroidTimezoneID(tzId.value))
                    }
                } catch(e: IllegalArgumentException) {
                    Logger.log.log(Level.WARNING, "Couldn't parse calendar default time zone", e)
                }
            }

            // add base values for Calendars
            values.putAll(calendarBaseValues)

            return values
        }

    }

    override val tag: String
        get() = "events-${account.name}-$id"

    override val title: String
        get() = displayName ?: id.toString()

    override var lastSyncState: SyncState?
        get() = provider.query(calendarSyncURI(), arrayOf(COLUMN_SYNC_STATE), null, null, null)?.use { cursor ->
                    if (cursor.moveToNext())
                        return SyncState.fromString(cursor.getString(0))
                    else
                        null
                }
        set(state) {
            val values = ContentValues(1)
            values.put(COLUMN_SYNC_STATE, state.toString())
            provider.update(calendarSyncURI(), values, null, null)
        }


    fun update(info: Collection, updateColor: Boolean) =
            update(valuesFromCollectionInfo(info, updateColor))


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
                Logger.log.log(Level.WARNING, "Couldn't check/increase sequence", e)
            }
            dirty += localEvent
        }

        return dirty
    }

    override fun findByName(name: String) =
            queryEvents("${Events._SYNC_ID}=?", arrayOf(name)).firstOrNull()


    override fun markNotDirty(flags: Int): Int {
        val values = ContentValues(1)
        values.put(LocalEvent.COLUMN_FLAGS, flags)
        return provider.update(Events.CONTENT_URI.asSyncAdapter(account), values,
                "${Events.CALENDAR_ID}=? AND NOT ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL",
                arrayOf(id.toString()))
    }

    override fun removeNotDirtyMarked(flags: Int): Int {
        var deleted = 0
        // list all non-dirty events with the given flags and delete every row + its exceptions
        provider.query(Events.CONTENT_URI.asSyncAdapter(account), arrayOf(Events._ID),
                "${Events.CALENDAR_ID}=? AND NOT ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NULL AND ${LocalEvent.COLUMN_FLAGS}=?",
                arrayOf(id.toString(), flags.toString()), null)?.use { cursor ->
            val batch = BatchOperation(provider)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                // delete event and possible exceptions (content provider doesn't delete exceptions itself)
                batch.enqueue(BatchOperation.CpoBuilder
                        .newDelete(Events.CONTENT_URI.asSyncAdapter(account))
                        .withSelection("${Events._ID}=? OR ${Events.ORIGINAL_ID}=?", arrayOf(id.toString(), id.toString())))
            }
            deleted = batch.commit()
        }
        return deleted
    }

    override fun forgetETags() {
        val values = ContentValues(1)
        values.putNull(LocalEvent.COLUMN_ETAG)
        provider.update(Events.CONTENT_URI.asSyncAdapter(account), values, "${Events.CALENDAR_ID}=?",
                arrayOf(id.toString()))
    }


    fun processDirtyExceptions() {
        // process deleted exceptions
        Logger.log.info("Processing deleted exceptions")
        provider.query(
                Events.CONTENT_URI.asSyncAdapter(account),
                arrayOf(Events._ID, Events.ORIGINAL_ID, LocalEvent.COLUMN_SEQUENCE),
                "${Events.CALENDAR_ID}=? AND ${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NOT NULL",
                arrayOf(id.toString()), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                Logger.log.fine("Found deleted exception, removing and re-scheduling original event (if available)")
                val id = cursor.getLong(0)             // can't be null (by definition)
                val originalID = cursor.getLong(1)     // can't be null (by query)

                val batch = BatchOperation(provider)

                // get original event's SEQUENCE
                provider.query(
                        ContentUris.withAppendedId(Events.CONTENT_URI, originalID).asSyncAdapter(account),
                        arrayOf(LocalEvent.COLUMN_SEQUENCE),
                        null, null, null)?.use { cursor2 ->
                    if (cursor2.moveToNext()) {
                        // original event is available
                        val originalSequence = if (cursor2.isNull(0)) 0 else cursor2.getInt(0)

                        // re-schedule original event and set it to DIRTY
                        batch.enqueue(BatchOperation.CpoBuilder
                                .newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, originalID).asSyncAdapter(account))
                                .withValue(LocalEvent.COLUMN_SEQUENCE, originalSequence + 1)
                                .withValue(Events.DIRTY, 1))
                    }
                }

                // completely remove deleted exception
                batch.enqueue(BatchOperation.CpoBuilder.newDelete(ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(account)))
                batch.commit()
            }
        }

        // process dirty exceptions
        Logger.log.info("Processing dirty exceptions")
        provider.query(
                Events.CONTENT_URI.asSyncAdapter(account),
                arrayOf(Events._ID, Events.ORIGINAL_ID, LocalEvent.COLUMN_SEQUENCE),
                "${Events.CALENDAR_ID}=? AND ${Events.DIRTY} AND ${Events.ORIGINAL_ID} IS NOT NULL",
                arrayOf(id.toString()), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                Logger.log.fine("Found dirty exception, increasing SEQUENCE to re-schedule")
                val id = cursor.getLong(0)             // can't be null (by definition)
                val originalID = cursor.getLong(1)     // can't be null (by query)
                val sequence = if (cursor.isNull(2)) 0 else cursor.getInt(2)

                val batch = BatchOperation(provider)
                // original event to DIRTY
                batch.enqueue(BatchOperation.CpoBuilder
                        .newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, originalID).asSyncAdapter(account))
                        .withValue(Events.DIRTY, 1))
                // increase SEQUENCE and set DIRTY to 0
                batch.enqueue(BatchOperation.CpoBuilder
                        .newUpdate(ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(account))
                        .withValue(LocalEvent.COLUMN_SEQUENCE, sequence + 1)
                        .withValue(Events.DIRTY, 0))
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
                    Logger.log.info("Marking event #$eventID without instances as deleted")
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