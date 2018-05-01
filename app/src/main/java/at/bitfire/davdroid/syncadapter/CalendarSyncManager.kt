/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import at.bitfire.dav4android.DavCalendar
import at.bitfire.dav4android.DavResource
import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.property.CalendarData
import at.bitfire.dav4android.property.GetCTag
import at.bitfire.dav4android.property.GetETag
import at.bitfire.dav4android.property.SyncToken
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.InvalidCalendarException
import okhttp3.HttpUrl
import okhttp3.RequestBody
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.util.*
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles events (VEVENT)
 */
class CalendarSyncManager(
        context: Context,
        settings: ISettings,
        account: Account,
        accountSettings: AccountSettings,
        extras: Bundle,
        authority: String,
        syncResult: SyncResult,
        localCalendar: LocalCalendar
): BaseDavSyncManager<LocalEvent, LocalCalendar, DavCalendar>(context, settings, account, accountSettings, extras, authority, syncResult, localCalendar) {

    companion object {
        const val MULTIGET_MAX_RESOURCES = 30
    }


    override fun prepare(): Boolean {
        if (!super.prepare())
            return false

        collectionURL = HttpUrl.parse(localCollection.name ?: return false) ?: return false
        davCollection = DavCalendar(httpClient.okHttpClient, collectionURL)

        // if there are dirty exceptions for events, mark their master events as dirty, too
        localCollection.processDirtyExceptions()

        return true
    }

    override fun queryCapabilities() {
        useRemoteCollection { it.propfind(0, GetCTag.NAME, SyncToken.NAME) }
    }

    override fun syncAlgorithm() = SyncAlgorithm.PROPFIND_REPORT

    override fun prepareUpload(resource: LocalEvent): RequestBody = useLocal(resource, {
        val event = requireNotNull(resource.event)
        Logger.log.log(Level.FINE, "Preparing upload of event ${resource.fileName}", event)

        val os = ByteArrayOutputStream()
        event.write(os)

        RequestBody.create(
                DavCalendar.MIME_ICALENDAR_UTF8,
                os.toByteArray()
        )
    })

    override fun listAllRemote(): Map<String, DavResource> {
        // calculate time range limits
        var limitStart: Date? = null
        accountSettings.getTimeRangePastDays()?.let { pastDays ->
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -pastDays)
            limitStart = calendar.time
        }

        return useRemoteCollection { remote ->
            // fetch list of remote VEVENTs and build hash table to index file name
            Logger.log.info("Querying events since $limitStart")
            remote.calendarQuery("VEVENT", limitStart, null)

            val result = LinkedHashMap<String, DavResource>(remote.members.size)
            for (iCal in remote.members) {
                val fileName = iCal.fileName()
                Logger.log.fine("Found remote VEVENT: $fileName")
                result[fileName] = iCal
            }
            result
        }
    }

    override fun processRemoteChanges(changes: RemoteChanges) {
        for (name in changes.deleted)
            localCollection.findByName(name)?.let {
                Logger.log.info("Deleting local event $name")
                useLocal(it, { local -> local.delete() })
                syncResult.stats.numDeletes++
            }

        val toDownload = changes.updated.map { it.location }
        Logger.log.info("Downloading ${toDownload.size} resources ($MULTIGET_MAX_RESOURCES at once)")

        for (bunch in toDownload.chunked(MULTIGET_MAX_RESOURCES)) {
            if (bunch.size == 1)
                // only one contact, use GET
                useRemote(DavResource(httpClient.okHttpClient, bunch.first()), { remote ->
                    val body = remote.get(DavCalendar.MIME_ICALENDAR.toString())

                    // CalDAV servers MUST return ETag on GET [https://tools.ietf.org/html/rfc4791#section-5.3.4]
                    val eTag = remote.properties[GetETag::class.java]?.eTag
                            ?: throw DavException("Received CalDAV GET response without ETag for ${remote.location}")

                    body.charStream().use { reader ->
                        processVEvent(remote.fileName(), eTag, reader)
                    }
                })
            else {
                // multiple contacts, use multi-get
                useRemoteCollection { it.multiget(bunch) }

                // process multiget results
                for (remote in davCollection.members)
                    useRemote(remote, {
                        val eTag = remote.properties[GetETag::class.java]?.eTag
                                ?: throw DavException("Received multi-get response without ETag")

                        val calendarData = remote.properties[CalendarData::class.java]
                        val iCalendar = calendarData?.iCalendar
                                ?: throw DavException("Received multi-get response without task data")

                        processVEvent(remote.fileName(), eTag, StringReader(iCalendar))
                    })
            }

            abortIfCancelled()
        }
    }


    // helpers

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
            useLocal(localCollection.findByName(fileName), { local ->
                if (local != null) {
                    Logger.log.info("Updating $fileName in local calendar")
                    local.eTag = eTag
                    local.update(newData)
                    syncResult.stats.numUpdates++
                } else {
                    Logger.log.info("Adding $fileName to local calendar")
                    useLocal(LocalEvent(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT), {
                        it.add()
                    })
                    syncResult.stats.numInserts++
                }
            })
        } else
            Logger.log.severe("Received VCALENDAR with not exactly one VEVENT with UID and without RECURRENCE-ID; ignoring $fileName")
    }

}
