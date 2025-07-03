/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.synctools.storage.calendar.AndroidEvent
import java.util.UUID

class LocalEvent(
    val androidEvent: AndroidEvent
) : LocalResource<Event> {

    // LocalResource implementation

    override val id: Long
        get() = androidEvent.id

    override val fileName: String?
        get() = androidEvent.syncId

    override var eTag: String?
        get() = androidEvent.eTag
        set(value) = TODO()

    override var scheduleTag: String?
        get() = androidEvent.scheduleTag
        set(value) = TODO()

    override val flags: Int
        get() = androidEvent.flags


    override fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {
        val values = contentValuesOf(
            AndroidEvent.COLUMN_ETAG to eTag,
            AndroidEvent.COLUMN_SCHEDULE_TAG to scheduleTag,
            AndroidEvent.COLUMN_SEQUENCE to androidEvent.event.sequence,   // why?
        )
        values.put(Events.DIRTY, 0)
        if (fileName != null)
            values.put(Events._SYNC_ID, fileName)
        androidEvent.update(values)
    }

    override fun delete() = androidEvent.delete()

    override fun prepareForUpload(): String {
        // make sure that UID is set
        val uid: String = androidEvent.event.uid ?: run {
            // generate new UID
            val newUid = UUID.randomUUID().toString()

            // update in calendar provider
            val values = contentValuesOf(Events.UID_2445 to newUid)
            androidEvent.update(values)

            // update this event
            androidEvent.event.uid = newUid

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

    override fun resetDeleted() {
        androidEvent.update(contentValuesOf(
            Events.DELETED to 0
        ))
    }

    override fun updateFlags(flags: Int) {
        androidEvent.update(contentValuesOf(
            AndroidEvent.COLUMN_FLAGS to flags
        ))
    }

    override fun updateFromDataObject(data: Event, eTag: String?, scheduleTag: String?) {
        androidEvent.calendar.updateEventFromDataObject(
            event = data,
            id = id,
            syncId = androidEvent.syncId,
            eTag = eTag,
            scheduleTag = scheduleTag,
            flags = flags
        )
    }



    // other methods

    val weAreOrganizer
        get() = androidEvent.event.isOrganizer == true

}