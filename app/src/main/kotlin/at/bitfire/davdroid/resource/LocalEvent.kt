/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.BuildConfig
import at.bitfire.synctools.icalendar.ICalendarWriter
import at.bitfire.synctools.mapping.calendar.AndroidEventProcessor
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.storage.calendar.AndroidRecurringCalendar
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import com.google.common.base.MoreObjects
import java.io.StringWriter
import java.util.Optional
import java.util.UUID

class LocalEvent(
    val recurringCalendar: AndroidRecurringCalendar,
    val androidEvent: EventAndExceptions
) : LocalResource {
    
    private val mainValues = androidEvent.main.entityValues

    override val id: Long
        get() = mainValues.getAsLong(Events._ID)

    override val fileName: String?
        get() = mainValues.getAsString(Events._SYNC_ID)

    override val eTag: String?
        get() = mainValues.getAsString(AndroidEvent2.COLUMN_ETAG)

    override val scheduleTag: String?
        get() = mainValues.getAsString(AndroidEvent2.COLUMN_SCHEDULE_TAG)

    override val flags: Int
        get() = mainValues.getAsInteger(AndroidEvent2.COLUMN_FLAGS)


    fun update(data: EventAndExceptions) {
        recurringCalendar.updateEventAndExceptions(id, data)
    }


    /**
     * Generates the event that should actually be uploaded:
     *
     * 1. Takes the [getCachedEvent].
     * 2. Calculates the new SEQUENCE.
     *
     * _Note: This method currently modifies the object returned by [getCachedEvent], but
     * this may change in the future._
     *
     * @return data object that should be used for uploading
     */
    fun eventToUpload(): String {
        // map entity
        val event = AndroidEventProcessor(
            accountName = recurringCalendar.calendar.account.name,
            prodIdGenerator = { packages ->
                val str = StringBuilder("DAVx5/${BuildConfig.VERSION_NAME}")
                if (packages.isNotEmpty()) {
                    str.append(" (")
                    str.append(packages.joinToString(", "))
                    str.append(")")
                }
                str.toString()
            }
        ).populate(androidEvent)

        // write to Calendar
        val iCal = StringWriter()
        ICalendarWriter().write(event, iCal)

        // TODO
        /*val nonGroupScheduled = event.attendees.isEmpty()
        val weAreOrganizer = event.isOrganizer == true

        // Increase sequence (event.sequence null/non-null behavior is defined by the Event, see KDoc of event.sequence):
        // - If it's null, the event has just been created in the database, so we can start with SEQUENCE:0 (default).
        // - If it's non-null, the event already exists on the server, so increase by one.
        val sequence = event.sequence
        if (sequence != null && (nonGroupScheduled || weAreOrganizer))
            event.sequence = sequence + 1*/

        return iCal.toString()
    }

    /**
     * Updates the SEQUENCE of the event in the content provider.
     *
     * @param sequence  new sequence value
     */
    fun updateSequence(sequence: Int?) {
        // TODO
        /*androidEvent.update(contentValuesOf(
            AndroidEvent2.COLUMN_SEQUENCE to sequence
        ))*/
    }


    /**
     * Creates and sets a new UID in the calendar provider, if no UID is already set.
     * It also returns the desired file name for the event for further processing in the sync algorithm.
     *
     * @return file name to use at upload
     */
    override fun prepareForUpload(): String {
        val uid = androidEvent.main.entityValues.getAsString(Events.UID_2445) ?: UUID.randomUUID().toString()
        // TODO
        /*
        // make sure that UID is set
        val uid: String = getCachedEvent().uid ?: run {
            // generate new UID
            val newUid = UUID.randomUUID().toString()

            // persist to calendar provider
            val values = contentValuesOf(Events.UID_2445 to newUid)
            androidEvent.update(values)

            // update in cached event data object
            getCachedEvent().uid = newUid

            newUid
        }*/

        val uidIsGoodFilename = uid.all { char ->
            // see RFC 2396 2.2
            char.isLetterOrDigit() || arrayOf(                  // allow letters and digits
                ';', ':', '@', '&', '=', '+', '$', ',',         // allow reserved characters except '/' and '?'
                '-', '_', '.', '!', '~', '*', '\'', '(', ')'    // allow unreserved characters
            ).contains(char)
        }
        return if (uidIsGoodFilename)
            "$uid.ics"                      // use UID as file name
        else
            "${UUID.randomUUID()}.ics"      // UID would be dangerous as file name, use random UUID instead
    }

    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        val values = contentValuesOf(
            Events.DIRTY to 0,
            AndroidEvent2.COLUMN_ETAG to eTag,
            AndroidEvent2.COLUMN_SCHEDULE_TAG to scheduleTag
        )
        if (fileName.isPresent)
            values.put(Events._SYNC_ID, fileName.get())
        recurringCalendar.calendar.updateEventRow(id, values)
    }

    override fun updateFlags(flags: Int) {
        recurringCalendar.calendar.updateEventRow(id, contentValuesOf(
            AndroidEvent2.COLUMN_FLAGS to flags
        ))
    }

    override fun deleteLocal() {
        recurringCalendar.deleteEventAndExceptions(id)
    }

    override fun resetDeleted() {
        recurringCalendar.calendar.updateEventRow(id, contentValuesOf(
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
            /*.add("event",
                try {
                    Ascii.truncate(getCachedEvent().toString(), 1000, "…")
                } catch (e: Exception) {
                    e
                }
            )*/.toString()

    override fun getViewUri(context: Context) =
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)

}