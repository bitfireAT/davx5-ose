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
import at.bitfire.dav4android.DavResponse
import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.property.*
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

    override fun queryCapabilities() =
            useRemoteCollection {
                it.propfind(0, SupportedReportSet.NAME, GetCTag.NAME, SyncToken.NAME).use { dav ->
                    dav[SupportedReportSet::class.java]?.let {
                        hasCollectionSync = it.reports.contains(SupportedReportSet.SYNC_COLLECTION)
                    }
                    Logger.log.info("Server supports Collection Sync: $hasCollectionSync")

                    syncState(dav)
                }
            }

    override fun syncAlgorithm() = if (accountSettings.getTimeRangePastDays() != null || !hasCollectionSync)
                SyncAlgorithm.PROPFIND_REPORT
            else
                SyncAlgorithm.COLLECTION_SYNC

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

    override fun listAllRemote(): Map<String, DavResponse> {
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
            remote.calendarQuery("VEVENT", limitStart, null).use { dav ->
                val result = LinkedHashMap<String, DavResponse>(dav.members.size)
                for (iCal in dav.members) {
                    val fileName = iCal.fileName()
                    Logger.log.fine("Found remote VEVENT: $fileName")
                    result[fileName] = iCal
                }
                result
            }
        }
    }

    override fun processRemoteChanges(changes: RemoteChanges) {
        for (name in changes.deleted)
            localCollection.findByName(name)?.let {
                Logger.log.info("Deleting local event $name")
                useLocal(it, { local -> local.delete() })
                syncResult.stats.numDeletes++
            }

        val toDownload = changes.updated.map { it.url }
        Logger.log.info("Downloading ${toDownload.size} resources ($MULTIGET_MAX_RESOURCES at once)")

        for (bunch in toDownload.chunked(MULTIGET_MAX_RESOURCES)) {
            if (bunch.size == 1)
                // only one contact, use GET
                useRemote(DavResource(httpClient.okHttpClient, bunch.first()), {
                    it.get(DavCalendar.MIME_ICALENDAR.toString()).use { dav ->
                        // CalDAV servers MUST return ETag on GET [https://tools.ietf.org/html/rfc4791#section-5.3.4]
                        val eTag = dav[GetETag::class.java]?.eTag
                                ?: throw DavException("Received CalDAV GET response without ETag for ${dav.url}")

                        dav.body?.charStream()?.use { reader ->
                            processVEvent(dav.fileName(), eTag, reader)
                        }
                    }
                })
            else {
                // multiple contacts, use multi-get
                useRemoteCollection {
                    it.multiget(bunch).use { dav ->

                    // process multiget results
                    for (remote in dav.members)
                        useRemote(remote, {
                            val eTag = remote[GetETag::class.java]?.eTag
                                    ?: throw DavException("Received multi-get response without ETag")

                            val calendarData = remote[CalendarData::class.java]
                            val iCalendar = calendarData?.iCalendar
                                    ?: throw DavException("Received multi-get response without task data")

                            processVEvent(remote.fileName(), eTag, StringReader(iCalendar))
                        })
                    }
                }
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
            Logger.log.info("Received VCALENDAR with not exactly one VEVENT with UID and without RECURRENCE-ID; ignoring $fileName")
    }

}
