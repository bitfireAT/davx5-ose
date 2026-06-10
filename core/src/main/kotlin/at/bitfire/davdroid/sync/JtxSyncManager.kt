/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.text.format.Formatter
import androidx.annotation.OpenForTesting
import at.bitfire.dav4jvm.okhttp.DavCalendar
import at.bitfire.dav4jvm.okhttp.MultiResponseCallback
import at.bitfire.dav4jvm.okhttp.Response
import at.bitfire.dav4jvm.okhttp.exception.DavException
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.caldav.CalendarData
import at.bitfire.dav4jvm.property.caldav.MaxResourceSize
import at.bitfire.dav4jvm.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.di.qualifier.SyncDispatcher
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.davdroid.resource.LocalJtxObject
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.synctools.exception.InvalidResourceException
import at.bitfire.synctools.icalendar.CalendarUidSplitter
import at.bitfire.synctools.icalendar.ICalendarGenerator
import at.bitfire.synctools.icalendar.ICalendarParser
import at.bitfire.synctools.mapping.jtx.JtxObjectBuilder
import at.bitfire.synctools.mapping.jtx.JtxObjectHandler
import at.bitfire.synctools.mapping.jtx.SequenceUpdater
import at.bitfire.synctools.mapping.jtx.handler.AndroidAttachmentFetcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.ProdId
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.util.logging.Level

class JtxSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted httpClient: OkHttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted localCollection: LocalJtxCollection,
    @Assisted collection: Collection,
    @Assisted resync: ResyncType?,
    private val productIds: ProductIds,
    @SyncDispatcher syncDispatcher: CoroutineDispatcher
): SyncManager<LocalJtxObject, LocalJtxCollection, DavCalendar>(
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
            httpClient: OkHttpClient,
            syncResult: SyncResult,
            localCollection: LocalJtxCollection,
            collection: Collection,
            resync: ResyncType?
        ): JtxSyncManager
    }


    override fun prepare(): Boolean {
        davCollection = DavCalendar(httpClient, collection.url)

        return true
    }

    override suspend fun queryCapabilities() =
        SyncException.wrapWithRemoteResourceSuspending(collection.url) {
            var syncState: SyncState? = null
            runInterruptible {
                davCollection.propfind(0, CalDAV.GetCTag, CalDAV.MaxResourceSize, WebDAV.SyncToken) { response, relation ->
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

    override fun generateUpload(resource: LocalJtxObject): GeneratedResource {
        val localJtxObject = resource.jtxObjectAndExceptions
        logger.log(Level.FINE, "Preparing upload of icalobject #${resource.id}", localJtxObject)

        // Increase SEQUENCE of main jtx object in memory and remember new value.
        // Will be written to provider later over onSuccessContext.
        val updatedSequence = SequenceUpdater().increaseSequence(localJtxObject.main)

        // Map jtx object to iCalendar (also generates UID, if necessary)
        val handler = JtxObjectHandler(
            prodId = ProdId(productIds.iCalProdId),
            attachmentFetcher = AndroidAttachmentFetcher(
                client = resource.collection.client,
                account = resource.collection.account
            )
        )
        val mappedJtxObjects = handler.mapToCalendarComponents(localJtxObject)

        // Persist UID if it was generated
        if (mappedJtxObjects.generatedUid) {
            resource.updateUid(mappedJtxObjects.uid)
        }

        // generate iCalendar and convert to request body
        val iCalWriter = StringWriter()
        ICalendarGenerator().write(mappedJtxObjects.associatedComponents, iCalWriter)
        val requestBody = iCalWriter.toString().toRequestBody(DavCalendar.MIME_ICALENDAR_UTF8)

        return GeneratedResource(
            suggestedFileName = DavUtils.fileNameFromUid(mappedJtxObjects.uid, "ics"),
            requestBody = requestBody,
            onSuccessContext = GeneratedResource.OnSuccessContext(
                sequence = updatedSequence
            )
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
                        val scheduleTag = response[ScheduleTag::class.java]?.scheduleTag
                        val fileName = response.href.lastSegment

                        try {
                            processICalObject(fileName, eTag, scheduleTag, StringReader(iCal))
                        } catch (e: InvalidResourceException) {
                            logger.log(Level.WARNING, "Error while processing jtx object", e)
                            notifyInvalidResource(e, fileName)
                        }
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
    internal fun processICalObject(fileName: String, eTag: String, scheduleTag: String?, reader: Reader) {
        val calendar = ICalendarParser().parse(reader)

        val uidsAndJournals = CalendarUidSplitter<CalendarComponent>().associateByUid(calendar, Component.VJOURNAL)
        val uidsAndTasks = CalendarUidSplitter<CalendarComponent>().associateByUid(calendar, Component.VTODO)

        if (uidsAndJournals.size + uidsAndTasks.size != 1) {
            logger.warning("Received iCalendar with not exactly one UID; ignoring $fileName")
            return
        }

        val uidsAndComponents = uidsAndJournals.ifEmpty { uidsAndTasks }
        val component = uidsAndComponents.values.first()

        val jtxEntityAndExceptions = JtxObjectBuilder(
            collectionId = localCollection.jtxCollection.id,
            fileName = fileName,
            eTag = eTag,
            scheduleTag = scheduleTag,
            flags = LocalResource.FLAG_REMOTELY_PRESENT
        ).build(component)

        val local = localCollection.findByName(fileName)
        if (local != null) {
            SyncException.wrapWithLocalResource(local) {
                logger.log(Level.INFO, "Updating $fileName in local jtx collection", component)
                local.update(jtxEntityAndExceptions)
            }
        } else {
            logger.log(Level.INFO, "Adding $fileName to local jtx collection", component)
            localCollection.add(jtxEntityAndExceptions)
        }
    }
}
