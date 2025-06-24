/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.resource.LocalEvent.Companion.numInstances
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.AndroidEventFactory
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.ICalendar
import at.bitfire.ical4android.ical4jVersion
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import net.fortuna.ical4j.model.property.ProdId
import java.util.UUID

class LocalEvent : AndroidEvent, LocalResource<Event> {

    companion object {
        init {
            ICalendar.prodId = ProdId("DAVx5/${BuildConfig.VERSION_NAME} ical4j/" + ical4jVersion)
        }

        /**
         * Marks the event as deleted
         * @param eventID
         */
        fun markAsDeleted(provider: ContentProviderClient, account: Account, eventID: Long) {
            provider.update(
                ContentUris.withAppendedId(
                    Events.CONTENT_URI,
                    eventID
                ).asSyncAdapter(account),
                contentValuesOf(Events.DELETED to 1),
                null, null
            )
        }


        /**
         * Finds the amount of direct instances this event has (without exceptions); used by [numInstances]
         * to find the number of instances of exceptions.
         *
         * The number of returned instances may vary with the Android version.
         *
         * @return number of direct event instances (not counting instances of exceptions); *null* if
         * the number can't be determined or if the event has no last date (recurring event without last instance)
         */
        fun numDirectInstances(provider: ContentProviderClient, account: Account, eventID: Long): Int? {
            // query event to get first and last instance
            var first: Long? = null
            var last: Long? = null
            provider.query(
                ContentUris.withAppendedId(
                    Events.CONTENT_URI,
                    eventID
                ),
                arrayOf(Events.DTSTART, Events.LAST_DATE), null, null, null
            )?.use { cursor ->
                cursor.moveToNext()
                if (!cursor.isNull(0))
                    first = cursor.getLong(0)
                if (!cursor.isNull(1))
                    last = cursor.getLong(1)
            }
            // if this event doesn't have a last occurence, it's endless and always has instances
            if (first == null || last == null)
                return null

            /* We can't use Long.MIN_VALUE and Long.MAX_VALUE because Android generates the instances
             on the fly and it doesn't accept those values. So we use the first/last actual occurence
             of the event (calculated by Android). */
            val instancesUri = CalendarContract.Instances.CONTENT_URI.asSyncAdapter(account)
                .buildUpon()
                .appendPath(first.toString())       // begin timestamp
                .appendPath(last.toString())        // end timestamp
                .build()

            var numInstances = 0
            provider.query(
                instancesUri, null,
                "${CalendarContract.Instances.EVENT_ID}=?", arrayOf(eventID.toString()),
                null
            )?.use { cursor ->
                numInstances += cursor.count
            }
            return numInstances
        }

        /**
         * Finds the total number of instances this event has (including instances of exceptions)
         *
         * The number of returned instances may vary with the Android version.
         *
         * @return number of direct event instances (not counting instances of exceptions); *null* if
         * the number can't be determined or if the event has no last date (recurring event without last instance)
         */
        fun numInstances(provider: ContentProviderClient, account: Account, eventID: Long): Int? {
            // num instances of the main event
            var numInstances = numDirectInstances(provider, account, eventID) ?: return null

            // add the number of instances of every main event's exception
            provider.query(
                Events.CONTENT_URI,
                arrayOf(Events._ID),
                "${Events.ORIGINAL_ID}=?", // get exception events of the main event
                arrayOf("$eventID"), null
            )?.use { exceptionsEventCursor ->
                while (exceptionsEventCursor.moveToNext()) {
                    val exceptionEventID = exceptionsEventCursor.getLong(0)
                    val exceptionInstances = numDirectInstances(provider, account, exceptionEventID)

                    if (exceptionInstances == null)
                    // number of instances of exception can't be determined; so the total number of instances is also unclear
                        return null

                    numInstances += exceptionInstances
                }
            }
            return numInstances
        }

    }

    override var fileName: String?
        get() = syncId
        private set(value) {
            syncId = value
        }

    val weAreOrganizer
        get() = event!!.isOrganizer == true


    constructor(calendar: AndroidCalendar<*>, event: Event, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int)
            : super(calendar, event, fileName, eTag, scheduleTag, flags)

    private constructor(calendar: AndroidCalendar<*>, values: ContentValues)
            : super(calendar, values)


    /**
     * Creates and sets a new UID in the calendar provider, if no UID is already set.
     * It also returns the desired file name for the event for further processing in the sync algorithm.
     *
     * @return file name to use at upload
     */
    override fun prepareForUpload(): String {
        // make sure that UID is set
        val uid: String = event!!.uid ?: run {
            // generate new UID
            val newUid = UUID.randomUUID().toString()

            // update in calendar provider
            val values = contentValuesOf(Events.UID_2445 to newUid)
            calendar.provider.update(eventSyncURI(), values, null, null)

            // update this event
            event?.uid = newUid

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
        val values = contentValuesOf(COLUMN_FLAGS to flags)
        calendar.provider.update(eventSyncURI(), values, null, null)

        this.flags = flags
    }

    override fun resetDeleted() {
        val values = contentValuesOf(Events.DELETED to 0)
        calendar.provider.update(eventSyncURI(), values, null, null)
    }

    object Factory : AndroidEventFactory<LocalEvent> {
        override fun fromProvider(calendar: AndroidCalendar<*>, values: ContentValues) =
            LocalEvent(calendar, values)
    }

}