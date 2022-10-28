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
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.LocalTask
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.Task
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.io.FileUtils
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles tasks (VTODO)
 */
class TasksSyncManager(
    context: Context,
    account: Account,
    accountSettings: AccountSettings,
    httpClient: HttpClient,
    extras: Bundle,
    authority: String,
    syncResult: SyncResult,
    localCollection: LocalTaskList
): SyncManager<LocalTask, LocalTaskList, DavCalendar>(context, account, accountSettings, httpClient, extras, authority, syncResult, localCollection) {

    override fun prepare(): Boolean {
        collectionURL = (localCollection.syncId ?: return false).toHttpUrlOrNull() ?: return false
        davCollection = DavCalendar(httpClient.okHttpClient, collectionURL)

        return true
    }

    override fun queryCapabilities() =
            remoteExceptionContext {
                var syncState: SyncState? = null
                it.propfind(0, MaxICalendarSize.NAME, GetCTag.NAME, SyncToken.NAME) { response, relation ->
                    if (relation == Response.HrefRelation.SELF) {
                        response[MaxICalendarSize::class.java]?.maxSize?.let { maxSize ->
                            Logger.log.info("Calendar accepts tasks up to ${FileUtils.byteCountToDisplaySize(maxSize)}")
                        }

                        syncState = syncState(response)
                    }
                }

                syncState
            }

    override fun syncAlgorithm() = SyncAlgorithm.PROPFIND_REPORT

    override fun generateUpload(resource: LocalTask): RequestBody = localExceptionContext(resource) {
        val task = requireNotNull(resource.task)
        Logger.log.log(Level.FINE, "Preparing upload of task ${resource.fileName}", task)

        val os = ByteArrayOutputStream()
        task.write(os)

        os.toByteArray().toRequestBody(DavCalendar.MIME_ICALENDAR_UTF8)
    }

    override fun listAllRemote(callback: MultiResponseCallback) {
        remoteExceptionContext { remote ->
            Logger.log.info("Querying tasks")
            remote.calendarQuery("VTODO", null, null, callback)
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

                    processVTodo(DavUtils.lastSegmentOfUrl(response.href), eTag, StringReader(iCal))
                }
            }
        }
    }

    override fun postProcess() {
        val touched = localCollection.touchRelations()
        Logger.log.info("Touched $touched relations")
    }

    // helpers

    private fun processVTodo(fileName: String, eTag: String, reader: Reader) {
        val tasks: List<Task>
        try {
            tasks = Task.tasksFromReader(reader)
        } catch (e: InvalidCalendarException) {
            Logger.log.log(Level.SEVERE, "Received invalid iCalendar, ignoring", e)
            notifyInvalidResource(e, fileName)
            return
        }

        if (tasks.size == 1) {
            val newData = tasks.first()

            // update local task, if it exists
            localExceptionContext(localCollection.findByName(fileName)) { local ->
                if (local != null) {
                    Logger.log.log(Level.INFO, "Updating $fileName in local task list", newData)
                    local.eTag = eTag
                    local.update(newData)
                    syncResult.stats.numUpdates++
                } else {
                    Logger.log.log(Level.INFO, "Adding $fileName to local task list", newData)
                    localExceptionContext(LocalTask(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)) {
                        it.add()
                    }
                    syncResult.stats.numInserts++
                }
            }
        } else
            Logger.log.info("Received VCALENDAR with not exactly one VTODO; ignoring $fileName")
    }

    override fun notifyInvalidResourceTitle(): String =
            context.getString(R.string.sync_invalid_task)

}