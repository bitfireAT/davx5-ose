/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.SyncResult
import android.text.format.Formatter
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
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.davdroid.resource.LocalJtxICalObject
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.JtxICalObject
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.util.logging.Level
import okhttp3.HttpUrl
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class JtxSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted accountSettings: AccountSettings,
    @Assisted extras: Array<String>,
    @Assisted httpClient: HttpClient,
    @Assisted authority: String,
    @Assisted syncResult: SyncResult,
    @Assisted localCollection: LocalJtxCollection,
    @Assisted collection: Collection
): SyncManager<LocalJtxICalObject, LocalJtxCollection, DavCalendar>(
    account,
    accountSettings,
    httpClient,
    extras,
    authority,
    syncResult,
    localCollection,
    collection
) {

    @AssistedFactory
    interface Factory {
        fun jtxSyncManager(
            account: Account,
            accountSettings: AccountSettings,
            extras: Array<String>,
            httpClient: HttpClient,
            authority: String,
            syncResult: SyncResult,
            localCollection: LocalJtxCollection,
            collection: Collection
        ): JtxSyncManager
    }


    override fun prepare() {
        davCollection = DavCalendar(httpClient.okHttpClient, collection.url)
    }

    override fun queryCapabilities() =
        SyncException.wrapWithRemoteResource(collection.url) {
            var syncState: SyncState? = null
            davCollection.propfind(0, GetCTag.NAME, MaxResourceSize.NAME, SyncToken.NAME) { response, relation ->
                if (relation == Response.HrefRelation.SELF) {
                    response[MaxResourceSize::class.java]?.maxSize?.let { maxSize ->
                        logger.info("Collection accepts resources up to ${Formatter.formatFileSize(context, maxSize)}")
                    }

                    syncState = syncState(response)
                }
            }
            syncState
        }

    override fun generateUpload(resource: LocalJtxICalObject): RequestBody =
        SyncException.wrapWithLocalResource(resource) {
            logger.log(Level.FINE, "Preparing upload of icalobject ${resource.fileName}", resource)
            val os = ByteArrayOutputStream()
            resource.write(os)
            os.toByteArray().toRequestBody(DavCalendar.MIME_ICALENDAR_UTF8)
        }

    override fun syncAlgorithm() = SyncAlgorithm.PROPFIND_REPORT

    override fun listAllRemote(callback: MultiResponseCallback) {
        SyncException.wrapWithRemoteResource(collection.url) {
            if (localCollection.supportsVTODO) {
                logger.info("Querying tasks")
                davCollection.calendarQuery("VTODO", null, null, callback)
            }

            if (localCollection.supportsVJOURNAL) {
                logger.info("Querying journals")
                davCollection.calendarQuery("VJOURNAL", null, null, callback)
            }
        }
    }

    override fun downloadRemote(bunch: List<HttpUrl>) {
        logger.info("Downloading ${bunch.size} iCalendars: $bunch")
        // multiple iCalendars, use calendar-multi-get
        SyncException.wrapWithRemoteResource(collection.url) {
            davCollection.multiget(bunch) { response, _ ->
                SyncException.wrapWithRemoteResource(response.href) wrapResource@ {
                    if (!response.isSuccess()) {
                        logger.warning("Received non-successful multiget response for ${response.href}")
                        return@wrapResource
                    }

                    val eTag = response[GetETag::class.java]?.eTag
                        ?: throw DavException("Received multi-get response without ETag")

                    val calendarData = response[CalendarData::class.java]
                    val iCal = calendarData?.iCalendar
                        ?: throw DavException("Received multi-get response without task data")

                    processICalObject(response.href.lastSegment, eTag, StringReader(iCal))
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
            logger.log(Level.SEVERE, "Received invalid iCalendar, ignoring", e)
            notifyInvalidResource(e, fileName)
            return
        }

        logger.log(Level.INFO, "Found ${icalobjects.size} entries in $fileName", icalobjects)

        icalobjects.forEach { jtxICalObject ->
            // if the entry is a recurring entry (and therefore has a recurid)
            // we udpate the existing (generated) entry
            if(jtxICalObject.recurid != null) {
                val local = localCollection.findRecurring(jtxICalObject.uid, jtxICalObject.recurid!!, jtxICalObject.dtstart!!)
                SyncException.wrapWithLocalResource(local) {
                    logger.log(Level.INFO, "Updating $fileName with recur instance ${jtxICalObject.recurid} in local list", jtxICalObject)
                    if(local != null) {
                        local.update(jtxICalObject)
                        syncResult.stats.numUpdates++
                    } else {
                        val newLocal = LocalJtxICalObject(localCollection, fileName, eTag, null, LocalResource.FLAG_REMOTELY_PRESENT)
                        SyncException.wrapWithLocalResource(newLocal) {
                            newLocal.applyNewData(jtxICalObject)
                            newLocal.add()
                        }
                        syncResult.stats.numInserts++
                    }
                }
            } else {
                // otherwise we insert or update the main entry
                val local = localCollection.findByName(fileName)
                SyncException.wrapWithLocalResource(local) {
                    if (local != null) {
                        logger.log(Level.INFO, "Updating $fileName in local list", jtxICalObject)
                        local.eTag = eTag
                        local.update(jtxICalObject)
                        syncResult.stats.numUpdates++
                    } else {
                        logger.log(Level.INFO, "Adding $fileName to local list", jtxICalObject)

                        val newLocal = LocalJtxICalObject(localCollection, fileName, eTag, null, LocalResource.FLAG_REMOTELY_PRESENT)
                        SyncException.wrapWithLocalResource(newLocal) {
                            newLocal.applyNewData(jtxICalObject)
                            newLocal.add()
                        }
                        syncResult.stats.numInserts++
                    }
                }
            }
        }
    }
}