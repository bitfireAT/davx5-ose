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
import at.bitfire.dav4jvm.DavCalendar
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.DavResponseCallback
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.property.*
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.SyncState
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.AccountSettings
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
        account: Account,
        accountSettings: AccountSettings,
        extras: Bundle,
        authority: String,
        syncResult: SyncResult,
        localCalendar: LocalCalendar
): SyncManager<LocalEvent, LocalCalendar, DavCalendar>(context, account, accountSettings, extras, authority, syncResult, localCalendar) {

    override fun prepare(): Boolean {
        collectionURL = HttpUrl.parse(localCollection.name ?: return false) ?: return false
        davCollection = DavCalendar(httpClient.okHttpClient, collectionURL)

        // if there are dirty exceptions for events, mark their master events as dirty, too
        localCollection.processDirtyExceptions()

        return true
    }

    override fun queryCapabilities(): SyncState? =
            useRemoteCollection {
                var syncState: SyncState? = null
                it.propfind(0, SupportedReportSet.NAME, GetCTag.NAME, SyncToken.NAME) { response, relation ->
                    if (relation == Response.HrefRelation.SELF) {
                        response[SupportedReportSet::class.java]?.let { supported ->
                            hasCollectionSync = supported.reports.contains(SupportedReportSet.SYNC_COLLECTION)
                        }

                        syncState = syncState(response)
                    }
                }

                Logger.log.info("Server supports Collection Sync: $hasCollectionSync")
                syncState
            }

    override fun syncAlgorithm() = if (accountSettings.getTimeRangePastDays() != null || !hasCollectionSync)
                SyncAlgorithm.PROPFIND_REPORT
            else
                SyncAlgorithm.COLLECTION_SYNC

    override fun prepareUpload(resource: LocalEvent): RequestBody = useLocal(resource) {
        val event = requireNotNull(resource.event)
        Logger.log.log(Level.FINE, "Preparing upload of event ${resource.fileName}", event)

        val os = ByteArrayOutputStream()
        event.write(os)

        RequestBody.create(
                DavCalendar.MIME_ICALENDAR_UTF8,
                os.toByteArray()
        )
    }

    override fun listAllRemote(callback: DavResponseCallback) {
        // calculate time range limits
        var limitStart: Date? = null
        accountSettings.getTimeRangePastDays()?.let { pastDays ->
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -pastDays)
            limitStart = calendar.time
        }

        return useRemoteCollection { remote ->
            Logger.log.info("Querying events since $limitStart")
            remote.calendarQuery("VEVENT", limitStart, null, callback)
        }
    }

    override fun downloadRemote(bunch: List<HttpUrl>) {
        Logger.log.info("Downloading ${bunch.size} iCalendars: $bunch")
        if (bunch.size == 1) {
            val remote = bunch.first()
            // only one contact, use GET
            useRemote(DavResource(httpClient.okHttpClient, remote)) { resource ->
                resource.get(DavCalendar.MIME_ICALENDAR.toString()) { response ->
                    // CalDAV servers MUST return ETag on GET [https://tools.ietf.org/html/rfc4791#section-5.3.4]
                    val eTag = response.header("ETag")?.let { GetETag(it).eTag }
                            ?: throw DavException("Received CalDAV GET response without ETag")

                    response.body()!!.use {
                        processVEvent(resource.fileName(), eTag, it.charStream())
                    }
                }
            }
        } else
            // multiple iCalendars, use calendar-multi-get
            useRemoteCollection {
                it.multiget(bunch) { response, _ ->
                    useRemote(response) {
                        if (!response.isSuccess()) {
                            Logger.log.warning("Received non-successful multiget response for ${response.href}")
                            return@useRemote
                        }

                        val eTag = response[GetETag::class.java]?.eTag
                                ?: throw DavException("Received multi-get response without ETag")

                        val calendarData = response[CalendarData::class.java]
                        val iCal = calendarData?.iCalendar
                                ?: throw DavException("Received multi-get response without address data")

                        processVEvent(DavUtils.lastSegmentOfUrl(response.href), eTag, StringReader(iCal))
                    }
                }
            }
    }

    override fun postProcess() {
    }


    // helpers

    private fun processVEvent(fileName: String, eTag: String, reader: Reader) {
        val events: List<Event>
        try {
            events = Event.fromReader(reader)
        } catch (e: InvalidCalendarException) {
            Logger.log.log(Level.SEVERE, "Received invalid iCalendar, ignoring", e)
            // TODO show notification
            return
        }

        if (events.size == 1) {
            val newData = events.first()

            // delete local event, if it exists
            useLocal(localCollection.findByName(fileName)) { local ->
                if (local != null) {
                    Logger.log.info("Updating $fileName in local calendar")
                    local.eTag = eTag
                    local.update(newData)
                    syncResult.stats.numUpdates++
                } else {
                    Logger.log.info("Adding $fileName to local calendar")
                    useLocal(LocalEvent(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)) {
                        it.add()
                    }
                    syncResult.stats.numInserts++
                }
            }
        } else
            Logger.log.info("Received VCALENDAR with not exactly one VEVENT with UID and without RECURRENCE-ID; ignoring $fileName")
    }

}
