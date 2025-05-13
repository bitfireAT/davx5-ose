/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
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
import at.bitfire.davdroid.di.SyncDispatcher
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.LocalTask
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.Task
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles tasks (VTODO)
 */
class TasksSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted httpClient: HttpClient,
    @Assisted extras: Array<String>,
    @Assisted authority: String,
    @Assisted syncResult: SyncResult,
    @Assisted localCollection: LocalTaskList,
    @Assisted collection: Collection,
    @SyncDispatcher syncDispatcher: CoroutineDispatcher
): SyncManager<LocalTask, LocalTaskList, DavCalendar>(
    account,
    httpClient,
    extras,
    authority,
    syncResult,
    localCollection,
    collection,
    syncDispatcher
) {

    @AssistedFactory
    interface Factory {
        fun tasksSyncManager(
            account: Account,
            httpClient: HttpClient,
            extras: Array<String>,
            authority: String,
            syncResult: SyncResult,
            localCollection: LocalTaskList,
            collection: Collection
        ): TasksSyncManager
    }


    override fun prepare(): Boolean {
        davCollection = DavCalendar(httpClient.okHttpClient, collection.url)

        return true
    }

    override fun queryCapabilities() =
        SyncException.wrapWithRemoteResource(collection.url) {
            var syncState: SyncState? = null
            davCollection.propfind(0, MaxResourceSize.NAME, GetCTag.NAME, SyncToken.NAME) { response, relation ->
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

    override fun generateUpload(resource: LocalTask): RequestBody =
        SyncException.wrapWithLocalResource(resource) {
            val task = requireNotNull(resource.task)
            logger.log(Level.FINE, "Preparing upload of task ${resource.fileName}", task)

            val os = ByteArrayOutputStream()
            task.write(os)

            os.toByteArray().toRequestBody(DavCalendar.MIME_ICALENDAR_UTF8)
        }

    override fun listAllRemote(callback: MultiResponseCallback) {
        SyncException.wrapWithRemoteResource(collection.url) {
            logger.info("Querying tasks")
            davCollection.calendarQuery("VTODO", null, null, callback)
        }
    }

    override fun downloadRemote(bunch: List<HttpUrl>) {
        logger.info("Downloading ${bunch.size} iCalendars: $bunch")
        // multiple iCalendars, use calendar-multi-get
        SyncException.wrapWithRemoteResource(collection.url) {
            davCollection.multiget(bunch) { response, _ ->
                // See CalendarSyncManager for more information about the multi-get response
                SyncException.wrapWithRemoteResource(response.href) wrapResource@ {
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

                    processVTodo(response.href.lastSegment, eTag, StringReader(iCal))
                }
            }
        }
    }

    override fun postProcess() {
        val touched = localCollection.touchRelations()
        logger.info("Touched $touched relations")
    }

    // helpers

    private fun processVTodo(fileName: String, eTag: String, reader: Reader) {
        val tasks: List<Task>
        try {
            tasks = Task.tasksFromReader(reader)
        } catch (e: InvalidCalendarException) {
            logger.log(Level.SEVERE, "Received invalid iCalendar, ignoring", e)
            notifyInvalidResource(e, fileName)
            return
        }

        if (tasks.size == 1) {
            val newData = tasks.first()

            // update local task, if it exists
            val local = localCollection.findByName(fileName)
            SyncException.wrapWithLocalResource(local) {
                if (local != null) {
                    logger.log(Level.INFO, "Updating $fileName in local task list", newData)
                    local.eTag = eTag
                    local.update(newData)
                } else {
                    logger.log(Level.INFO, "Adding $fileName to local task list", newData)
                    val newLocal = LocalTask(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                    SyncException.wrapWithLocalResource(newLocal) {
                        newLocal.add()
                    }
                }
            }
        } else
            logger.info("Received VCALENDAR with not exactly one VTODO; ignoring $fileName")
    }

    override fun notifyInvalidResourceTitle(): String =
            context.getString(R.string.sync_invalid_task)

}