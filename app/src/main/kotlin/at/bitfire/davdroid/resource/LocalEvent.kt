/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.LegacyAndroidCalendar
import at.bitfire.synctools.icalendar.AssociatedEvents
import at.bitfire.synctools.mapping.calendar.LegacyAndroidEventBuilder2
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.storage.calendar.AndroidRecurringCalendar
import java.util.Optional
import java.util.UUID

class LocalEvent(
    val recurringCalendar: AndroidRecurringCalendar,
    val androidEvent: AndroidEvent2
) : LocalResource<AssociatedEvents> {

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


    override fun updateFromRemote(data: AssociatedEvents, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int) {
        val eventAndExceptions = LegacyAndroidEventBuilder2(
            calendar = androidEvent.calendar,
            event = data,
            id = id,
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

        val nonGroupScheduled = event.attendees.isEmpty()
        val weAreOrganizer = event.isOrganizer == true

        // Increase sequence (event.sequence null/non-null behavior is defined by the Event, see KDoc of event.sequence):
        // - If it's null, the event has just been created in the database, so we can start with SEQUENCE:0 (default).
        // - If it's non-null, the event already exists on the server, so increase by one.
        val sequence = event.sequence
        if (sequence != null && (nonGroupScheduled || weAreOrganizer))
            event.sequence = sequence + 1

        return event
    }

    /**
     * Updates the SEQUENCE of the event in the content provider.
     *
     * @param sequence  new sequence value
     */
    fun updateSequence(sequence: Int?) {
        androidEvent.update(contentValuesOf(
            AndroidEvent2.COLUMN_SEQUENCE to sequence
        ))
    }


    /**
     * Creates and sets a new UID in the calendar provider, if no UID is already set.
     * It also returns the desired file name for the event for further processing in the sync algorithm.
     *
     * @return file name to use at upload
     */
    override fun prepareForUpload(): String {
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
        }

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

}