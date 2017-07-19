/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import at.bitfire.davdroid.BuildConfig
import at.bitfire.ical4android.*
import net.fortuna.ical4j.model.property.ProdId
import java.io.FileNotFoundException
import java.util.*

class LocalEvent: AndroidEvent, LocalResource {

    companion object {
        init {
            iCalendar.prodId = ProdId("+//IDN bitfire.at//DAVdroid/" + BuildConfig.VERSION_NAME + " ical4j/2.x")
        }

        val COLUMN_ETAG = CalendarContract.Events.SYNC_DATA1
        val COLUMN_UID = if (Build.VERSION.SDK_INT >= 17) Events.UID_2445 else Events.SYNC_DATA2
        val COLUMN_SEQUENCE = CalendarContract.Events.SYNC_DATA3
    }

    override var fileName: String? = null
    override var eTag: String? = null

    var weAreOrganizer = true


    constructor(calendar: AndroidCalendar<*>, event: Event, fileName: String?, eTag: String?): super(calendar, event) {
        this.fileName = fileName
        this.eTag = eTag
    }

    private constructor(calendar: AndroidCalendar<*>, id: Long, baseInfo: ContentValues?): super(calendar, id, baseInfo) {
        baseInfo?.let {
            fileName = it.getAsString(Events._SYNC_ID)
            eTag = it.getAsString(COLUMN_ETAG)
        }
    }


    /* process LocalEvent-specific fields */

    @Throws(FileNotFoundException::class, CalendarStorageException::class)
    override fun populateEvent(row: ContentValues) {
        super.populateEvent(row)
        val event = requireNotNull(event)

        fileName = row.getAsString(Events._SYNC_ID)
        eTag = row.getAsString(COLUMN_ETAG)
        event.uid = row.getAsString(COLUMN_UID)

        event.sequence = row.getAsInteger(COLUMN_SEQUENCE)
        if (Build.VERSION.SDK_INT >= 17) {
            val isOrganizer = row.getAsInteger(Events.IS_ORGANIZER)
            weAreOrganizer = isOrganizer != null && isOrganizer != 0
        } else {
            val organizer = row.getAsString(Events.ORGANIZER)
            weAreOrganizer = organizer == null || organizer == calendar.account.name
        }
    }

    @Throws(FileNotFoundException::class, CalendarStorageException::class)
    override fun buildEvent(recurrence: Event?, builder: ContentProviderOperation.Builder) {
        super.buildEvent(recurrence, builder)
        val event = requireNotNull(event)

        val buildException = recurrence != null
        val eventToBuild = recurrence ?: event

        builder .withValue(COLUMN_UID, event.uid)
                .withValue(COLUMN_SEQUENCE, eventToBuild.sequence)
                .withValue(CalendarContract.Events.DIRTY, 0)
                .withValue(CalendarContract.Events.DELETED, 0)

        if (buildException)
            builder .withValue(Events.ORIGINAL_SYNC_ID, fileName)
        else
            builder .withValue(Events._SYNC_ID, fileName)
                    .withValue(COLUMN_ETAG, eTag)
    }


    /* custom queries */

    @Throws(CalendarStorageException::class)
    override fun prepareForUpload() {
        try {
            var uid: String? = null
            calendar.provider.query(eventSyncURI(), arrayOf(COLUMN_UID), null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    uid = cursor.getString(0)
            }
            if (uid == null)
                uid = UUID.randomUUID().toString()

            val newFileName = "$uid.ics"

            val values = ContentValues(2)
            values.put(Events._SYNC_ID, newFileName)
            values.put(COLUMN_UID, uid)
            calendar.provider.update(eventSyncURI(), values, null, null)

            fileName = newFileName
            event!!.uid = uid

        } catch(e: Exception) {
            throw CalendarStorageException("Couldn't update UID", e)
        }
    }

    @Throws(CalendarStorageException::class)
    override fun clearDirty(eTag: String?) {
        try {
            val values = ContentValues(2)
            values.put(CalendarContract.Events.DIRTY, 0)
            values.put(COLUMN_ETAG, eTag)
            values.put(COLUMN_SEQUENCE, event!!.sequence);
            calendar.provider.update(eventSyncURI(), values, null, null)

            this.eTag = eTag
        } catch (e: Exception) {
            throw CalendarStorageException("Couldn't update UID", e)
        }
    }


    object Factory: AndroidEventFactory<LocalEvent> {

        override fun newInstance(calendar: AndroidCalendar<*>, id: Long, baseInfo: ContentValues?) =
                LocalEvent(calendar, id, baseInfo)

        override fun newInstance(calendar: AndroidCalendar<*>, event: Event) =
                LocalEvent(calendar, event, null, null)

    }
}
