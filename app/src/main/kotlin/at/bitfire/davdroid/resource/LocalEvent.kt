/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentValues
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.LegacyAndroidCalendar
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import java.util.UUID

class LocalEvent(
    val androidEvent: AndroidEvent
) : LocalResource<Event> {

    // LocalResource implementation

    override val id: Long?
        get() = androidEvent.id

    override var fileName: String?
        get() = androidEvent.syncId
        private set(value) {
            androidEvent.syncId = value
        }

    override var eTag: String?
        get() = androidEvent.eTag
        set(value) { androidEvent.eTag = value }

    override var scheduleTag: String?
        get() = androidEvent.scheduleTag
        set(value) { androidEvent.scheduleTag = value }

    override val flags: Int
        get() = androidEvent.flags

    override fun update(data: Event) = androidEvent.update(data)

    override fun delete() = androidEvent.delete()


    // other methods

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

    override fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {
        val values = ContentValues(5)
        if (fileName != null)
            values.put(Events._SYNC_ID, fileName)
        values.put(AndroidEvent2.COLUMN_ETAG, eTag)
        values.put(AndroidEvent2.COLUMN_SCHEDULE_TAG, scheduleTag)
        values.put(Events.DIRTY, 0)
        androidEvent.update(values)

        if (fileName != null)
            this.fileName = fileName
        this.eTag = eTag
        this.scheduleTag = scheduleTag
    }

    override fun updateFlags(flags: Int) {
        val values = contentValuesOf(AndroidEvent2.COLUMN_FLAGS to flags)
        androidEvent.update(values)

        androidEvent.flags = flags
    }

    override fun resetDeleted() {
        val values = contentValuesOf(Events.DELETED to 0)
        androidEvent.update(values)
    }

}