/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import at.bitfire.dav4android.DavCalendar
import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.property.CalendarData
import at.bitfire.dav4android.property.GetCTag
import at.bitfire.dav4android.property.GetETag
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.InvalidCalendarException
import okhttp3.HttpUrl
import okhttp3.RequestBody
import org.apache.commons.collections4.ListUtils
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.util.*
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles events ({@code VEVENT}).
 */
class CalendarSyncManager(
        context: Context,
        settings: ISettings,
        account: Account,
        accountSettings: AccountSettings,
        extras: Bundle,
        authority: String,
        syncResult: SyncResult,
        val provider: ContentProviderClient,
        val localCalendar: LocalCalendar
): SyncManager(context, settings, account, accountSettings, extras, authority, syncResult, "calendar/${localCalendar.id}") {

    companion object {
        private val MAX_MULTIGET = 20
    }

    init {
        localCollection = localCalendar
    }

    override fun notificationId() = Constants.NOTIFICATION_CALENDAR_SYNC

    override fun getSyncErrorTitle() = context.getString(R.string.sync_error_calendar, account.name)!!


    override fun prepare(): Boolean {
        collectionURL = HttpUrl.parse(localCalendar.name ?: return false) ?: return false
        davCollection = DavCalendar(httpClient.okHttpClient, collectionURL)
        return true
    }

    override fun queryCapabilities() {
        davCollection.propfind(0, GetCTag.NAME)
    }

    override fun prepareDirty() {
        super.prepareDirty()
        localCalendar.processDirtyExceptions()
    }

    override fun prepareUpload(resource: LocalResource): RequestBody {
        if (resource is LocalEvent) {
            val event = requireNotNull(resource.event)
            Logger.log.log(Level.FINE, "Preparing upload of event ${resource.fileName}", event)

            val os = ByteArrayOutputStream()
            event.write(os)

            return RequestBody.create(
                    DavCalendar.MIME_ICALENDAR,
                    os.toByteArray()
            )
        } else
            throw IllegalArgumentException("resource must be a LocalEvent")
    }

    override fun listRemote() {
        // calculate time range limits
        var limitStart: Date? = null
        accountSettings.getTimeRangePastDays()?.let { pastDays ->
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -pastDays)
            limitStart = calendar.time
        }

        // fetch list of remote VEVENTs and build hash table to index file name
        val calendar = davCalendar()
        currentDavResource = calendar
        calendar.calendarQuery("VEVENT", limitStart, null)

        remoteResources = HashMap(davCollection.members.size)
        for (iCal in davCollection.members) {
            val fileName = iCal.fileName()
            Logger.log.fine("Found remote VEVENT: $fileName")
            remoteResources[fileName] = iCal
        }

        currentDavResource = null
    }

    override fun downloadRemote() {
        Logger.log.info("Downloading ${toDownload.size} events ($MAX_MULTIGET at once)")

        // download new/updated iCalendars from server
        for (bunch in ListUtils.partition(toDownload.toList(), MAX_MULTIGET)) {
            abortIfCancelled()
            Logger.log.info("Downloading ${bunch.joinToString(", ")}")

            if (bunch.size == 1) {
                // only one contact, use GET
                val remote = bunch.first()
                currentDavResource = remote

                val body = remote.get("text/calendar")

                // CalDAV servers MUST return ETag on GET [https://tools.ietf.org/html/rfc4791#section-5.3.4]
                val eTag = remote.properties[GetETag::class.java]
                if (eTag == null || eTag.eTag.isNullOrEmpty())
                    throw DavException("Received CalDAV GET response without ETag for ${remote.location}")

                body.charStream()?.use { reader ->
                    processVEvent(remote.fileName(), eTag.eTag!!, reader)
                }

            } else {
                // multiple contacts, use multi-get
                val calendar = davCalendar()
                currentDavResource = calendar
                calendar.multiget(bunch.map { it.location })

                // process multiget results
                for (remote in davCollection.members) {
                    currentDavResource = remote

                    val eTag = remote.properties[GetETag::class.java]?.eTag
                            ?: throw DavException("Received multi-get response without ETag")

                    val calendarData = remote.properties[CalendarData::class.java]
                    val iCalendar = calendarData?.iCalendar
                            ?: throw DavException("Received multi-get response without event data")

                    processVEvent(remote.fileName(), eTag, StringReader(iCalendar))
                }
            }

            currentDavResource = null
        }
    }


    // helpers

    private fun davCalendar() = davCollection as DavCalendar

    private fun processVEvent(fileName: String, eTag: String, reader: Reader) {
        val events: List<Event>
        try {
            events = Event.fromReader(reader)
        } catch (e: InvalidCalendarException) {
            Logger.log.log(Level.SEVERE, "Received invalid iCalendar, ignoring", e)
            return
        }

        if (events.size == 1) {
            val newData = events.first()

            // delete local event, if it exists
            val localEvent = localResources[fileName] as LocalEvent?
            currentLocalResource = localEvent
            if (localEvent != null) {
                Logger.log.info("Updating $fileName in local calendar")
                localEvent.eTag = eTag
                localEvent.update(newData)
                syncResult.stats.numUpdates++
            } else {
                Logger.log.info("Adding $fileName to local calendar")
                val newEvent = LocalEvent(localCalendar, newData, fileName, eTag)
                currentLocalResource = newEvent
                newEvent.add()
                syncResult.stats.numInserts++
            }
        } else
            Logger.log.severe("Received VCALENDAR with not exactly one VEVENT with UID, but without RECURRENCE-ID; ignoring $fileName")

        currentLocalResource = null
    }

}
