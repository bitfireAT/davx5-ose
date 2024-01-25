/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import at.bitfire.dav4jvm.DavCalendar
import at.bitfire.dav4jvm.MultiResponseCallback
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.property.caldav.CalendarData
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.dav4jvm.property.caldav.MaxResourceSize
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.davdroid.resource.LocalJtxICalObject
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils
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
    extras: Array<String>,
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
            it.propfind(0, GetCTag.NAME, MaxResourceSize.NAME, SyncToken.NAME) { response, relation ->
                if (relation == Response.HrefRelation.SELF) {
                    response[MaxResourceSize::class.java]?.maxSize?.let { maxSize ->
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
                        ?: throw DavException("Received multi-get response without task data")

                    processICalObject(DavUtils.lastSegmentOfUrl(response.href), eTag, StringReader(iCal))
                }
            }
        }
    }

    override fun postProcess() {
        localCollection.updateLastSync()
    }

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

        Logger.log.log(Level.INFO, "Found ${icalobjects.size} entries in $fileName", icalobjects)

        icalobjects.forEach { jtxICalObject ->
            // if the entry is a recurring entry (and therefore has a recurid)
            // we udpate the existing (generated) entry
            if(jtxICalObject.recurid != null) {
                localExceptionContext(localCollection.findRecurring(jtxICalObject.uid, jtxICalObject.recurid!!, jtxICalObject.dtstart!!)) { local ->
                    Logger.log.log(Level.INFO, "Updating $fileName with recur instance ${jtxICalObject.recurid} in local list", jtxICalObject)
                    if(local != null) {
                        local.update(jtxICalObject)
                        syncResult.stats.numUpdates++
                    } else {
                        localExceptionContext(LocalJtxICalObject(localCollection, fileName, eTag, null, LocalResource.FLAG_REMOTELY_PRESENT)) {
                            it.applyNewData(jtxICalObject)
                            it.add()
                        }
                        syncResult.stats.numInserts++
                    }
                }
            } else {
                // otherwise we insert or update the main entry
                localExceptionContext(localCollection.findByName(fileName)) { local ->
                    if (local != null) {
                        Logger.log.log(Level.INFO, "Updating $fileName in local list", jtxICalObject)
                        local.eTag = eTag
                        local.update(jtxICalObject)
                        syncResult.stats.numUpdates++
                    } else {
                        Logger.log.log(Level.INFO, "Adding $fileName to local list", jtxICalObject)

                        localExceptionContext(LocalJtxICalObject(localCollection, fileName, eTag, null, LocalResource.FLAG_REMOTELY_PRESENT)) {
                            it.applyNewData(jtxICalObject)
                            it.add()
                        }
                        syncResult.stats.numInserts++
                    }
                }
            }
        }
    }
}