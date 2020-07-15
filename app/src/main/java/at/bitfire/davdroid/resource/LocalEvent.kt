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
import android.provider.CalendarContract.Events
import at.bitfire.davdroid.BuildConfig
import at.bitfire.ical4android.*
import net.fortuna.ical4j.model.property.ProdId
import org.apache.commons.lang3.StringUtils
import java.util.*

class LocalEvent: AndroidEvent, LocalResource<Event> {

    companion object {
        init {
            ICalendar.prodId = ProdId("${BuildConfig.userAgent}/${BuildConfig.VERSION_NAME} ical4j/" + Ical4Android.ical4jVersion)
        }

        const val COLUMN_ETAG = Events.SYNC_DATA1
        const val COLUMN_FLAGS = Events.SYNC_DATA2
        const val COLUMN_SEQUENCE = Events.SYNC_DATA3
        const val COLUMN_SCHEDULE_TAG = Events.SYNC_DATA4
    }

    override var fileName: String? = null
        private set

    override var eTag: String? = null
    override var scheduleTag: String? = null

    override var flags: Int = 0
        private set

    var weAreOrganizer = false
        private set


    constructor(calendar: AndroidCalendar<*>, event: Event, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int): super(calendar, event) {
        this.fileName = fileName
        this.eTag = eTag
        this.scheduleTag = scheduleTag
        this.flags = flags
    }

    private constructor(calendar: AndroidCalendar<*>, values: ContentValues): super(calendar, values) {
        fileName = values.getAsString(Events._SYNC_ID)
        eTag = values.getAsString(COLUMN_ETAG)
        scheduleTag = values.getAsString(COLUMN_SCHEDULE_TAG)
        flags = values.getAsInteger(COLUMN_FLAGS) ?: 0
    }

    override fun populateEvent(row: ContentValues, groupScheduled: Boolean) {
        val event = requireNotNull(event)

        event.uid = row.getAsString(Events.UID_2445)
        event.sequence = row.getAsInteger(COLUMN_SEQUENCE)

        val isOrganizer = row.getAsInteger(Events.IS_ORGANIZER)
        weAreOrganizer = isOrganizer != null && isOrganizer != 0

        super.populateEvent(row, groupScheduled)
    }

    override fun buildEvent(recurrence: Event?, builder: ContentProviderOperation.Builder) {
        super.buildEvent(recurrence, builder)
        val event = requireNotNull(event)

        val buildException = recurrence != null
        val eventToBuild = recurrence ?: event

        builder .withValue(Events.UID_2445, event.uid)
                .withValue(COLUMN_SEQUENCE, eventToBuild.sequence)
                .withValue(Events.DIRTY, 0)
                .withValue(Events.DELETED, 0)
                .withValue(COLUMN_FLAGS, flags)

        if (buildException)
            builder .withValue(Events.ORIGINAL_SYNC_ID, fileName)
        else
            builder .withValue(Events._SYNC_ID, fileName)
                    .withValue(COLUMN_ETAG, eTag)
                    .withValue(COLUMN_SCHEDULE_TAG, scheduleTag)
    }


    override fun prepareForUpload(): String {
        var uid: String? = null
        calendar.provider.query(eventSyncURI(), arrayOf(Events.UID_2445), null, null, null)?.use { cursor ->
            if (cursor.moveToNext())
                uid = StringUtils.trimToNull(cursor.getString(0))
        }

        if (uid == null) {
            // generate new UID
            uid = UUID.randomUUID().toString()

            val values = ContentValues(1)
            values.put(Events.UID_2445, uid)
            calendar.provider.update(eventSyncURI(), values, null, null)

            event!!.uid = uid
        }

        return "$uid.ics"
    }

    override fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {
        val values = ContentValues(5)
        if (fileName != null)
            values.put(Events._SYNC_ID, fileName)
        values.put(COLUMN_ETAG, eTag)
        values.put(COLUMN_SCHEDULE_TAG, scheduleTag)
        values.put(COLUMN_SEQUENCE, event!!.sequence)
        values.put(Events.DIRTY, 0)
        calendar.provider.update(eventSyncURI(), values, null, null)

        if (fileName != null)
            this.fileName = fileName
        this.eTag = eTag
        this.scheduleTag = scheduleTag
    }

    override fun updateFlags(flags: Int) {
        val values = ContentValues(1)
        values.put(COLUMN_FLAGS, flags)
        calendar.provider.update(eventSyncURI(), values, null, null)

        this.flags = flags
    }


    object Factory: AndroidEventFactory<LocalEvent> {
        override fun fromProvider(calendar: AndroidCalendar<*>, values: ContentValues) =
                LocalEvent(calendar, values)
    }

}
