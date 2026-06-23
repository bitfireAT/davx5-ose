/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.text.format.Formatter
import at.bitfire.dav4jvm.ktor.DavCalendar
import at.bitfire.dav4jvm.ktor.MultiResponseCallback
import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.caldav.CalendarData
import at.bitfire.dav4jvm.property.caldav.MaxResourceSize
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.di.qualifier.SyncDispatcher
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.LocalTask
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.synctools.exception.InvalidResourceException
import at.bitfire.synctools.icalendar.AssociatedTasks
import at.bitfire.synctools.icalendar.CalendarUidSplitter
import at.bitfire.synctools.icalendar.ICalendarGenerator
import at.bitfire.synctools.icalendar.ICalendarParser
import at.bitfire.synctools.mapping.tasks.DmfsTaskBuilder
import at.bitfire.synctools.mapping.tasks.DmfsTaskHandler
import at.bitfire.synctools.mapping.tasks.SequenceUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.ProdId
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles tasks (VTODO)
 */
class TasksSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted httpClient: HttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted localCollection: LocalTaskList,
    @Assisted collection: Collection,
    @Assisted resync: ResyncType?,
    private val productIds: ProductIds,
    @SyncDispatcher syncDispatcher: CoroutineDispatcher
): SyncManager<LocalTask, LocalTaskList, DavCalendar>(
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
        fun tasksSyncManager(
            account: Account,
            httpClient: HttpClient,
            syncResult: SyncResult,
            localCollection: LocalTaskList,
            collection: Collection,
            resync: ResyncType?
        ): TasksSyncManager
    }


    override fun prepare(): Boolean {
        davCollection = DavCalendar(httpClient, collection.url)

        return true
    }

    override suspend fun queryCapabilities() =
        SyncException.wrapWithRemoteResourceSuspending(collection.url) {
            var syncState: SyncState? = null
            davCollection.propfind(0, CalDAV.MaxResourceSize, CalDAV.GetCTag, WebDAV.SyncToken) { response, relation ->
                if (relation == Response.HrefRelation.SELF) {
                    response[MaxResourceSize::class.java]?.maxSize?.let { maxSize ->
                        logger.info("Calendar accepts tasks up to ${Formatter.formatFileSize(context, maxSize)}")
                    }

                    syncState = syncState(response)
                }
            }

            syncState
        }

    override fun syncAlgorithm() = SyncAlgorithm.PROPFIND_REPORT

    override fun generateUpload(resource: LocalTask): GeneratedResource {
        val localTask = resource.taskAndExceptions
        logger.log(Level.FINE, "Preparing upload of task #${resource.id}", localTask)

        /* Increase SEQUENCE of main task in memory and remember new value.
        Will be written to provider later over onSuccessContext. */
        val updatedSequence = SequenceUpdater().increaseSequence(localTask.main)

        // map Android event to iCalendar (also generates UID, if necessary)
        val handler = DmfsTaskHandler(ProdId(productIds.iCalProdId))
        val mappedVToDos = handler.mapToVToDos(localTask)

        // persist UID if it was generated
        if (mappedVToDos.generatedUid)
            resource.updateUid(mappedVToDos.uid)

        // generate iCalendar and convert to request body
        val iCalWriter = StringWriter()
        ICalendarGenerator().write(mappedVToDos.associatedTasks, iCalWriter)
        val outgoingContent = iCalWriter.toOutgoingContent(DavCalendar.MIME_ICALENDAR_UTF8)

        return GeneratedResource(
            suggestedFileName = DavUtils.fileNameFromUid(mappedVToDos.uid, "ics"),
            content = outgoingContent,
            onSuccessContext = GeneratedResource.OnSuccessContext(
                sequence = updatedSequence
            )
        )
    }

    override suspend fun listAllRemote(callback: MultiResponseCallback) {
        SyncException.wrapWithRemoteResourceSuspending(collection.url) {
            logger.info("Querying tasks")
            davCollection.calendarQuery("VTODO", null, null, callback = callback)
        }
    }

    override suspend fun downloadRemote(bunch: List<Url>) {
        logger.info("Downloading ${bunch.size} iCalendars: $bunch")
        // multiple iCalendars, use calendar-multi-get
        SyncException.wrapWithRemoteResourceSuspending(collection.url) {
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

                    val fileName = response.href.lastSegment

                    try {
                        processVTodo(fileName, eTag, StringReader(iCal))
                    } catch (e: InvalidResourceException) {
                        logger.log(Level.WARNING, "Error while processing VTODO", e)
                        notifyInvalidResource(e, fileName)
                    }
                }
            }
        }
    }

    override fun postProcess() {
        val touched = localCollection.dmfsTaskList.touchRelations()
        logger.info("Touched $touched relations")
    }


    // helpers

    private fun processVTodo(fileName: String, eTag: String, reader: Reader) {
        val calendar = ICalendarParser().parse(reader)

        val uidsAndTasks = CalendarUidSplitter<VToDo>().associateByUid(calendar, Component.VTODO)
        if (uidsAndTasks.size != 1) {
            logger.warning("Received iCalendar with not exactly one UID; ignoring $fileName")
            return
        }
        // Task: main VTODO and potentially attached exceptions (further VTODOs with RECURRENCE-ID)
        val task: AssociatedTasks = uidsAndTasks.values.first()

        // map AssociatedTasks (VTODOs) to TaskAndExceptions (task provider tasks)
        val dmfsTask = DmfsTaskBuilder(
            taskList = localCollection.dmfsTaskList,
            syncId = fileName,
            eTag = eTag,
            flags = LocalResource.FLAG_REMOTELY_PRESENT
        ).build(task)

        // update local task, if it exists
        val local = localCollection.findByName(fileName)
        if (local != null) {
            SyncException.wrapWithLocalResource(local) {
                logger.log(Level.INFO, "Updating $fileName in local task list", task)
                local.update(dmfsTask)
            }
        } else {
            logger.log(Level.INFO, "Adding $fileName to local task list", task)
            localCollection.add(dmfsTask)
        }
    }

    override fun notifyInvalidResourceTitle(): String =
            context.getString(R.string.sync_invalid_task)

}