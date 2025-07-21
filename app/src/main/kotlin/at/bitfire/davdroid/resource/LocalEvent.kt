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

    val event: Event by lazy {
        val legacyCalendar = LegacyAndroidCalendar(androidEvent.calendar)
        legacyCalendar.getEvent(androidEvent.id) ?: throw LocalStorageException("Event ${androidEvent.id} not found")
    }


    /**
     * Creates and sets a new UID in the calendar provider, if no UID is already set.
     * It also returns the desired file name for the event for further processing in the sync algorithm.
     *
     * @return file name to use at upload
     */
    override fun prepareForUpload(): String {
        // make sure that UID is set
        val uid: String = event.uid ?: run {
            // generate new UID
            val newUid = UUID.randomUUID().toString()

            // persist to calendar provider
            val values = contentValuesOf(Events.UID_2445 to newUid)
            androidEvent.update(values)

            // update in event data object
            event.uid = newUid

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

    @Deprecated("Use add...() of specific collection implementation", level = DeprecationLevel.ERROR)
    override fun add() = throw NotImplementedError()

    override fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {
        val values = ContentValues(5)
        if (fileName != null)
            values.put(Events._SYNC_ID, fileName)
        values.put(AndroidEvent2.COLUMN_ETAG, eTag)
        values.put(AndroidEvent2.COLUMN_SCHEDULE_TAG, scheduleTag)
        values.put(AndroidEvent2.COLUMN_SEQUENCE, event.sequence)
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