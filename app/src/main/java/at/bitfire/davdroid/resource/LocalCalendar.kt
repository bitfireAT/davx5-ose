/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.*
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.SyncState
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidCalendarFactory
import at.bitfire.ical4android.BatchOperation
import at.bitfire.ical4android.DateUtils
import java.util.*
import java.util.logging.Level

class LocalCalendar private constructor(
        account: Account,
        provider: ContentProviderClient,
        id: Long
): AndroidCalendar<LocalEvent>(account, provider, LocalEvent.Factory, id), LocalCollection<LocalEvent> {

    companion object {

        private const val COLUMN_SYNC_STATE = Calendars.CAL_SYNC1

        fun create(account: Account, provider: ContentProviderClient, info: CollectionInfo): Uri {
            val values = valuesFromCollectionInfo(info, true)

            // ACCOUNT_NAME and ACCOUNT_TYPE are required (see docs)! If it's missing, other apps will crash.
            values.put(Calendars.ACCOUNT_NAME, account.name)
            values.put(Calendars.ACCOUNT_TYPE, account.type)
            values.put(Calendars.OWNER_ACCOUNT, account.name)

            // flag as visible & synchronizable at creation, might be changed by user at any time
            values.put(Calendars.VISIBLE, 1)
            values.put(Calendars.SYNC_EVENTS, 1)
            return create(account, provider, values)
        }

        private fun valuesFromCollectionInfo(info: CollectionInfo, withColor: Boolean): ContentValues {
            val values = ContentValues()
            values.put(Calendars.NAME, info.url)
            values.put(Calendars.CALENDAR_DISPLAY_NAME, if (info.displayName.isNullOrBlank()) DavUtils.lastSegmentOfUrl(info.url) else info.displayName)

            if (withColor)
                values.put(Calendars.CALENDAR_COLOR, info.color ?: Constants.DAVDROID_GREEN_RGBA)

            if (info.readOnly || info.forceReadOnly)
                values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ)
            else {
                values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                values.put(Calendars.CAN_MODIFY_TIME_ZONE, 1)
                values.put(Calendars.CAN_ORGANIZER_RESPOND, 1)
            }

            info.timeZone?.let { tzData ->
                try {
                    val timeZone = DateUtils.parseVTimeZone(tzData)
                    timeZone.timeZoneId?.let { tzId ->
                        values.put(Calendars.CALENDAR_TIME_ZONE, DateUtils.findAndroidTimezoneID(tzId.value))
                    }
                } catch(e: IllegalArgumentException) {
                    Logger.log.log(Level.WARNING, "Couldn't parse calendar default time zone", e)
                }
            }
            values.put(Calendars.ALLOWED_REMINDERS, "${Reminders.METHOD_ALERT},${Reminders.METHOD_EMAIL}")
            values.put(Calendars.ALLOWED_AVAILABILITY, "${Reminders.AVAILABILITY_TENTATIVE},${Reminders.AVAILABILITY_FREE},${Reminders.AVAILABILITY_BUSY}")
            values.put(Calendars.ALLOWED_ATTENDEE_TYPES, "${CalendarContract.Attendees.TYPE_OPTIONAL},${CalendarContract.Attendees.TYPE_REQUIRED},${CalendarContract.Attendees.TYPE_RESOURCE}")
            return values
        }
    }

    override val title: String
        get() = displayName ?: id.toString()

    override var lastSyncState: SyncState?
        get() = provider.query(calendarSyncURI(), arrayOf(COLUMN_SYNC_STATE), null, null, null)?.let { cursor ->
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


    fun update(info: CollectionInfo, updateColor: Boolean) =
            update(valuesFromCollectionInfo(info, updateColor))


    override fun findDeleted() =
            queryEvents("${Events.DELETED}!=0 AND ${Events.ORIGINAL_ID} IS NULL", null)

    override fun findDirty(): List<LocalEvent> {
        val dirty = LinkedList<LocalEvent>()

        // get dirty events which are required to have an increased SEQUENCE value
        for (localEvent in queryEvents("${Events.DIRTY}!=0 AND ${Events.ORIGINAL_ID} IS NULL", null)) {
            val event = localEvent.event!!
            val sequence = event.sequence
            if (event.sequence == null)      // sequence has not been assigned yet (i.e. this event was just locally created)
                event.sequence = 0
            else if (localEvent.weAreOrganizer)
                event.sequence = sequence!! + 1
            dirty += localEvent
        }

        return dirty
    }

    override fun findByName(name: String) =
            queryEvents("${Events._SYNC_ID}=?", arrayOf(name)).firstOrNull()


    override fun markNotDirty(flags: Int): Int {
        val values = ContentValues(1)
        values.put(LocalEvent.COLUMN_FLAGS, flags)
        return provider.update(eventsSyncURI(), values,
                "${Events.CALENDAR_ID}=? AND ${Events.DIRTY}=0 AND ${Events.ORIGINAL_ID} IS NULL",
                arrayOf(id.toString()))
    }

    override fun removeNotDirtyMarked(flags: Int) =
            provider.delete(eventsSyncURI(),
                    "${Events.CALENDAR_ID}=? AND ${Events.DIRTY}=0 AND ${Events.ORIGINAL_ID} IS NULL AND ${LocalEvent.COLUMN_FLAGS}=?",
                    arrayOf(id.toString(), flags.toString()))


    fun processDirtyExceptions() {
        // process deleted exceptions
        Logger.log.info("Processing deleted exceptions")
        provider.query(
                syncAdapterURI(Events.CONTENT_URI),
                arrayOf(Events._ID, Events.ORIGINAL_ID, LocalEvent.COLUMN_SEQUENCE),
                "${Events.CALENDAR_ID}=? AND ${Events.DELETED}!=0 AND ${Events.ORIGINAL_ID} IS NOT NULL",
                arrayOf(id.toString()), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                Logger.log.fine("Found deleted exception, removing and re-scheduling original event (if available)")
                val id = cursor.getLong(0)             // can't be null (by definition)
                val originalID = cursor.getLong(1)     // can't be null (by query)

                val batch = BatchOperation(provider)

                // get original event's SEQUENCE
                provider.query(
                        syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, originalID)),
                        arrayOf(LocalEvent.COLUMN_SEQUENCE),
                        null, null, null)?.use { cursor2 ->
                    if (cursor2.moveToNext()) {
                        // original event is available
                        val originalSequence = if (cursor2.isNull(0)) 0 else cursor2.getInt(0)

                        // re-schedule original event and set it to DIRTY
                        batch.enqueue(BatchOperation.Operation(
                                ContentProviderOperation.newUpdate(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, originalID)))
                                        .withValue(LocalEvent.COLUMN_SEQUENCE, originalSequence + 1)
                                        .withValue(Events.DIRTY, 1)
                        ))
                    }
                }

                // completely remove deleted exception
                batch.enqueue(BatchOperation.Operation(
                        ContentProviderOperation.newDelete(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id)))
                ))
                batch.commit()
            }
        }

        // process dirty exceptions
        Logger.log.info("Processing dirty exceptions")
        provider.query(
                syncAdapterURI(Events.CONTENT_URI),
                arrayOf(Events._ID, Events.ORIGINAL_ID, LocalEvent.COLUMN_SEQUENCE),
                "${Events.CALENDAR_ID}=? AND ${Events.DIRTY}!=0 AND ${Events.ORIGINAL_ID} IS NOT NULL",
                arrayOf(id.toString()), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                Logger.log.fine("Found dirty exception, increasing SEQUENCE to re-schedule")
                val id = cursor.getLong(0)             // can't be null (by definition)
                val originalID = cursor.getLong(1)     // can't be null (by query)
                val sequence = if (cursor.isNull(2)) 0 else cursor.getInt(2)

                val batch = BatchOperation(provider)
                // original event to DIRTY
                batch.enqueue(BatchOperation.Operation (
                        ContentProviderOperation.newUpdate(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, originalID)))
                                .withValue(Events.DIRTY, 1)
                ))
                // increase SEQUENCE and set DIRTY to 0
                batch.enqueue(BatchOperation.Operation (
                        ContentProviderOperation.newUpdate(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id)))
                                .withValue(LocalEvent.COLUMN_SEQUENCE, sequence + 1)
                                .withValue(Events.DIRTY, 0)
                ))
                batch.commit()
            }
        }
    }


    object Factory: AndroidCalendarFactory<LocalCalendar> {

        override fun newInstance(account: Account, provider: ContentProviderClient, id: Long) =
            LocalCalendar(account, provider, id)

    }

}