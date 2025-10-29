/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.LegacyAndroidCalendar
import at.bitfire.synctools.mapping.calendar.LegacyAndroidEventBuilder2
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.storage.calendar.AndroidRecurringCalendar
import com.google.common.base.Ascii
import com.google.common.base.MoreObjects
import java.util.Optional

class LocalEvent(
    val recurringCalendar: AndroidRecurringCalendar,
    val androidEvent: AndroidEvent2
) : LocalResource {

    override val id: Long
        get() = androidEvent.id

    override val fileName: String?
        get() = androidEvent.syncId

    override val eTag: String?
        get() = androidEvent.eTag

    override val scheduleTag: String?
        get() = androidEvent.scheduleTag

    override val flags: Int
        get() = androidEvent.flags


    fun update(data: Event, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int) {
        val eventAndExceptions = LegacyAndroidEventBuilder2(
            calendar = androidEvent.calendar,
            event = data,
            syncId = fileName,
            eTag = eTag,
            scheduleTag = scheduleTag,
            flags = flags
        ).build()
        recurringCalendar.updateEventAndExceptions(id, eventAndExceptions)
    }


    private var _event: Event? = null
    /**
     * Retrieves the event from the content provider and converts it to a legacy data object.
     *
     * Caches the result: the content provider is only queried at the first call and then
     * this method always returns the same object.
     *
     * @throws LocalStorageException    if there is no local event with the ID from [androidEvent]
     */
    @Synchronized
    fun getCachedEvent(): Event {
        _event?.let { return it }

        val legacyCalendar = LegacyAndroidCalendar(androidEvent.calendar)
        val event = legacyCalendar.getEvent(androidEvent.id)
            ?: throw LocalStorageException("Event ${androidEvent.id} not found")

        _event = event
        return event
    }

    /**
     * Generates the [Event] that should actually be uploaded:
     *
     * 1. Takes the [getCachedEvent].
     * 2. Calculates the new SEQUENCE.
     *
     * _Note: This method currently modifies the object returned by [getCachedEvent], but
     * this may change in the future._
     *
     * @return data object that should be used for uploading
     */
    fun eventToUpload(): Event {
        val event = getCachedEvent()


        return event
    }

    /**
     * Updates the SEQUENCE of the event in the content provider.
     *
     * @param sequence  new sequence value
     */
    fun updateSequence(sequence: Int) {
        androidEvent.update(contentValuesOf(
            AndroidEvent2.COLUMN_SEQUENCE to sequence
        ))
    }

    override fun updateUid(uid: String) {
        androidEvent.update(contentValuesOf(
            Events.UID_2445 to uid
        ))
    }


    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        val values = contentValuesOf(
            Events.DIRTY to 0,
            AndroidEvent2.COLUMN_ETAG to eTag,
            AndroidEvent2.COLUMN_SCHEDULE_TAG to scheduleTag
        )
        if (fileName.isPresent)
            values.put(Events._SYNC_ID, fileName.get())
        androidEvent.update(values)
    }

    override fun updateFlags(flags: Int) {
        androidEvent.update(contentValuesOf(
            AndroidEvent2.COLUMN_FLAGS to flags
        ))
    }

    override fun deleteLocal() {
        recurringCalendar.deleteEventAndExceptions(id)
    }

    override fun resetDeleted() {
        androidEvent.update(contentValuesOf(
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
                    Ascii.truncate(getCachedEvent().toString(), 1000, "…")
                } catch (e: Exception) {
                    e
                }
            ).toString()

    override fun getViewUri(context: Context) =
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)

}