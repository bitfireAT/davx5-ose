/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import at.bitfire.dav4jvm.DavCalendar
import at.bitfire.dav4jvm.MultiResponseCallback
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.property.*
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.davdroid.resource.LocalJtxICalObject
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.JtxICalObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.io.FileUtils
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.util.logging.Level

class JtxSyncManager(
    context: Context,
    account: Account,
    accountSettings: AccountSettings,
    extras: Bundle,
    httpClient: HttpClient,
    authority: String,
    syncResult: SyncResult,
    localCollection: LocalJtxCollection
):  SyncManager<LocalJtxICalObject, LocalJtxCollection, DavCalendar>(context, account, accountSettings, httpClient, extras, authority, syncResult, localCollection) {

    override fun prepare(): Boolean {
        collectionURL = (localCollection.url ?: return false).toHttpUrlOrNull() ?: return false
        davCollection = DavCalendar(httpClient.okHttpClient, collectionURL)

        return true
    }

    override fun queryCapabilities() =
        remoteExceptionContext {
            var syncState: SyncState? = null
            it.propfind(0, GetCTag.NAME, MaxICalendarSize.NAME, SyncToken.NAME) { response, relation ->
                if (relation == Response.HrefRelation.SELF) {
                    response[MaxICalendarSize::class.java]?.maxSize?.let { maxSize ->
                        Logger.log.info("Collection accepts resources up to ${FileUtils.byteCountToDisplaySize(maxSize)}")
                    }

                    syncState = syncState(response)
                }
            }
            syncState
        }

    override fun generateUpload(resource: LocalJtxICalObject): RequestBody = localExceptionContext(resource) {
        Logger.log.log(Level.FINE, "Preparing upload of icalobject ${resource.fileName}", resource)
        val os = ByteArrayOutputStream()
        resource.write(os)
        os.toByteArray().toRequestBody(DavCalendar.MIME_ICALENDAR_UTF8)
    }

    override fun syncAlgorithm() = SyncAlgorithm.PROPFIND_REPORT

    override fun listAllRemote(callback: MultiResponseCallback) {
        remoteExceptionContext { remote ->
            if (localCollection.supportsVTODO) {
                Logger.log.info("Querying tasks")
                remote.calendarQuery("VTODO", null, null, callback)
            }

            if (localCollection.supportsVJOURNAL) {
                Logger.log.info("Querying journals")
                remote.calendarQuery("VJOURNAL", null, null, callback)
            }
        }
    }

    override fun downloadRemote(bunch: List<HttpUrl>) {
        Logger.log.info("Downloading ${bunch.size} iCalendars: $bunch")
        // multiple iCalendars, use calendar-multi-get
        remoteExceptionContext {
            it.multiget(bunch) { response, _ ->
                responseExceptionContext(response) {
                    if (!response.isSuccess()) {
                        Logger.log.warning("Received non-successful multiget response for ${response.href}")
                        return@responseExceptionContext
                    }

                    val eTag = response[GetETag::class.java]?.eTag
                        ?: throw DavException("Received multi-get response without ETag")

                    val calendarData = response[CalendarData::class.java]
                    val iCal = calendarData?.iCalendar
                        ?: throw DavException("Received multi-get response without address data")

                    processICalObject(DavUtils.lastSegmentOfUrl(response.href), eTag, StringReader(iCal))
                }
            }
        }
    }

    override fun postProcess() { /* nothing to do */  }

    override fun notifyInvalidResourceTitle(): String =
        context.getString(R.string.sync_invalid_event)


    private fun processICalObject(fileName: String, eTag: String, reader: Reader) {
        val icalobjects: MutableList<JtxICalObject> = mutableListOf()
        try {
            // parse the reader content and return the list of ICalObjects
            icalobjects.addAll(JtxICalObject.fromReader(reader, localCollection))
        } catch (e: InvalidCalendarException) {
            Logger.log.log(Level.SEVERE, "Received invalid iCalendar, ignoring", e)
            notifyInvalidResource(e, fileName)
            return
        }

        if (icalobjects.size == 1) {
            val newData = icalobjects.first()

            // update local task, if it exists
            localExceptionContext(localCollection.findByName(fileName)) { local ->
                if (local != null) {
                    Logger.log.log(Level.INFO, "Updating $fileName in local task list", newData)
                    local.eTag = eTag
                    local.update(newData)
                    syncResult.stats.numUpdates++
                } else {
                    Logger.log.log(Level.INFO, "Adding $fileName to local task list", newData)

                    localExceptionContext(LocalJtxICalObject(localCollection, fileName, eTag, null, LocalResource.FLAG_REMOTELY_PRESENT)) {
                        it.applyNewData(newData)
                        it.add()
                    }
                    syncResult.stats.numInserts++
                }
            }
        } else
            Logger.log.info("Received VCALENDAR with not exactly one VTODO or VJOURNAL; ignoring $fileName")
    }

}