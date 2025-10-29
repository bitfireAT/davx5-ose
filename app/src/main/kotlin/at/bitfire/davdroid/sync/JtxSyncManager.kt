/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.text.format.Formatter
import androidx.annotation.OpenForTesting
import at.bitfire.dav4jvm.DavCalendar
import at.bitfire.dav4jvm.MultiResponseCallback
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.property.caldav.CalendarData
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.dav4jvm.property.caldav.MaxResourceSize
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.di.SyncDispatcher
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.davdroid.resource.LocalJtxICalObject
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.ical4android.JtxICalObject
import at.bitfire.synctools.exception.InvalidICalendarException
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.util.logging.Level

class JtxSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted httpClient: HttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted localCollection: LocalJtxCollection,
    @Assisted collection: Collection,
    @Assisted resync: ResyncType?,
    @SyncDispatcher syncDispatcher: CoroutineDispatcher
): SyncManager<LocalJtxICalObject, LocalJtxCollection, DavCalendar>(
    account,
    httpClient,
    SyncDataType.TASKS,
    syncResult,
    localCollection,
    collection,
    resync,
    syncDispatcher
) {

    @AssistedFactory
    interface Factory {
        fun jtxSyncManager(
            account: Account,
            httpClient: HttpClient,
            syncResult: SyncResult,
            localCollection: LocalJtxCollection,
            collection: Collection,
            resync: ResyncType?
        ): JtxSyncManager
    }


    override fun prepare(): Boolean {
        davCollection = DavCalendar(httpClient.okHttpClient, collection.url)

        return true
    }

    override suspend fun queryCapabilities() =
        SyncException.wrapWithRemoteResourceSuspending(collection.url) {
            var syncState: SyncState? = null
            runInterruptible {
                davCollection.propfind(0, GetCTag.NAME, MaxResourceSize.NAME, SyncToken.NAME) { response, relation ->
                    if (relation == Response.HrefRelation.SELF) {
                        response[MaxResourceSize::class.java]?.maxSize?.let { maxSize ->
                            logger.info("Collection accepts resources up to ${Formatter.formatFileSize(context, maxSize)}")
                        }

                        syncState = syncState(response)
                    }
                }
            }
            syncState
        }

    override fun generateUpload(resource: LocalJtxICalObject): GeneratedResource {
        logger.log(Level.FINE, "Preparing upload of icalobject ${resource.fileName}", resource)
        val os = ByteArrayOutputStream()
        resource.write(os, Constants.iCalProdId)

        return GeneratedResource(
            suggestedFileName = DavUtils.fileNameFromUid(resource.uid, "ics"),
            requestBody = os.toByteArray().toRequestBody(DavCalendar.MIME_ICALENDAR_UTF8),
            onSuccessContext = GeneratedResource.OnSuccessContext()     // nothing special to update after upload
        )
    }

    override fun syncAlgorithm() = SyncAlgorithm.PROPFIND_REPORT

    override suspend fun listAllRemote(callback: MultiResponseCallback) {
        SyncException.wrapWithRemoteResourceSuspending(collection.url) {
            if (localCollection.supportsVTODO) {
                logger.info("Querying tasks")
                runInterruptible {
                    davCollection.calendarQuery("VTODO", null, null, callback)
                }
            }

            if (localCollection.supportsVJOURNAL) {
                logger.info("Querying journals")
                runInterruptible {
                    davCollection.calendarQuery("VJOURNAL", null, null, callback)
                }
            }
        }
    }

    override suspend fun downloadRemote(bunch: List<HttpUrl>) {
        logger.info("Downloading ${bunch.size} iCalendars: $bunch")
        // multiple iCalendars, use calendar-multi-get
        SyncException.wrapWithRemoteResourceSuspending(collection.url) {
            runInterruptible {
                davCollection.multiget(bunch) { response, _ ->
                    // See CalendarSyncManager for more information about the multi-get response
                    SyncException.wrapWithRemoteResource(response.href) wrapResource@{
                        if (!response.isSuccess()) {
                            logger.warning("Ignoring non-successful multi-get response for ${response.href}")
                            return@wrapResource
                        }

                        val iCal = response[CalendarData::class.java]?.iCalendar
                        if (iCal == null) {
                            logger.warning("Ignoring multi-get response without calendar-data")
                            return@wrapResource
                        }

                        val eTag = response[GetETag::class.java]?.eTag
                            ?: throw DavException("Received multi-get response without ETag")

                        processICalObject(response.href.lastSegment, eTag, StringReader(iCal))
                    }
                }
            }
        }
    }

    override fun postProcess() {
        localCollection.updateLastSync()
    }

    override fun notifyInvalidResourceTitle(): String =
        context.getString(R.string.sync_invalid_event)


    @OpenForTesting
    internal fun processICalObject(fileName: String, eTag: String, reader: Reader) {
        val icalobjects: MutableList<JtxICalObject> = mutableListOf()
        try {
            // parse the reader content and return the list of ICalObjects
            icalobjects.addAll(JtxICalObject.fromReader(reader, localCollection))
        } catch (e: InvalidICalendarException) {
            logger.log(Level.SEVERE, "Received invalid iCalendar, ignoring", e)
            notifyInvalidResource(e, fileName)
            return
        }

        logger.log(Level.INFO, "Found ${icalobjects.size} entries in $fileName", icalobjects)

        icalobjects.forEach { jtxICalObject ->
            // if the entry is a recurring entry (and therefore has a recurid)
            // we update the existing (generated) entry
            val recurid = jtxICalObject.recurid
            if(recurid != null) {
                val local = localCollection.findRecurInstance(jtxICalObject.uid, recurid)
                SyncException.wrapWithLocalResource(local) {
                    logger.log(Level.INFO, "Updating $fileName with recur instance $recurid in local list", jtxICalObject)
                    if(local != null) {
                        local.update(jtxICalObject)
                    } else {
                        val newLocal = LocalJtxICalObject(localCollection, fileName, eTag, null, LocalResource.FLAG_REMOTELY_PRESENT)
                        SyncException.wrapWithLocalResource(newLocal) {
                            newLocal.applyNewData(jtxICalObject)
                            newLocal.add()
                        }
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
                    } else {
                        logger.log(Level.INFO, "Adding $fileName to local list", jtxICalObject)

                        val newLocal = LocalJtxICalObject(localCollection, fileName, eTag, null, LocalResource.FLAG_REMOTELY_PRESENT)
                        SyncException.wrapWithLocalResource(newLocal) {
                            newLocal.applyNewData(jtxICalObject)
                            newLocal.add()
                        }
                    }
                }
            }
        }
    }
}