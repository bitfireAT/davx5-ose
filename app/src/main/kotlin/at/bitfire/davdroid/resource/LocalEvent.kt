/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidRecurringCalendar
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import at.bitfire.synctools.storage.calendar.EventsContract
import com.google.common.base.Ascii
import com.google.common.base.MoreObjects
import java.util.Optional

class LocalEvent(
    val recurringCalendar: AndroidRecurringCalendar,
    val androidEvent: EventAndExceptions
) : LocalResource {

    val calendar: AndroidCalendar
        get() = recurringCalendar.calendar
    
    private val mainValues = androidEvent.main.entityValues

    override val id: Long
        get() = mainValues.getAsLong(Events._ID)

    override val fileName: String?
        get() = mainValues.getAsString(Events._SYNC_ID)

    override val eTag: String?
        get() = mainValues.getAsString(EventsContract.COLUMN_ETAG)

    override val scheduleTag: String?
        get() = mainValues.getAsString(EventsContract.COLUMN_SCHEDULE_TAG)

    override val flags: Int
        get() = mainValues.getAsInteger(EventsContract.COLUMN_FLAGS)


    fun update(data: EventAndExceptions) {
        recurringCalendar.updateEventAndExceptions(id, data)
    }


    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        val values = contentValuesOf(
            Events.DIRTY to 0,
            EventsContract.COLUMN_ETAG to eTag,
            EventsContract.COLUMN_SCHEDULE_TAG to scheduleTag
        )
        if (fileName.isPresent)
            values.put(Events._SYNC_ID, fileName.get())
        calendar.updateEventRow(id, values)
    }

    override fun updateFlags(flags: Int) {
        calendar.updateEventRow(id, contentValuesOf(
            EventsContract.COLUMN_FLAGS to flags
        ))
    }

    override fun updateSequence(sequence: Int) {
        calendar.updateEventRow(id, contentValuesOf(
            EventsContract.COLUMN_SEQUENCE to sequence
        ))
    }

    override fun updateUid(uid: String) {
        // TODO update exceptions
        // TODO remove Google UID row
        calendar.updateEventRow(id, contentValuesOf(
            Events.UID_2445 to uid
        ))
    }

    override fun deleteLocal() {
        recurringCalendar.deleteEventAndExceptions(id)
    }

    override fun resetDeleted() {
        calendar.updateEventRow(id, contentValuesOf(
            Events.DELETED to 0
        ))
    }

    override fun getDebugSummary() =
        MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("fileName", fileName)
            .add("eTag", eTag)
            .add("scheduleTag", scheduleTag)
            .add("flags", flags)
            .add("event",
                try {
                    Ascii.truncate(androidEvent.toString(), 1000, "…")
                } catch (e: Exception) {
                    e
                }
            ).toString()

    override fun getViewUri(context: Context) =
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)

}