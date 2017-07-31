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
import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.property.CalendarData
import at.bitfire.dav4android.property.GetCTag
import at.bitfire.dav4android.property.GetETag
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Logger
import at.bitfire.davdroid.R
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.LocalTask
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.TaskProvider
import okhttp3.HttpUrl
import okhttp3.RequestBody
import org.apache.commons.collections4.ListUtils
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.util.*
import java.util.logging.Level

class TasksSyncManager(
        context: Context,
        account: Account,
        settings: AccountSettings,
        extras: Bundle,
        authority: String,
        syncResult: SyncResult,
        val provider: TaskProvider,
        val localTaskList: LocalTaskList
): SyncManager(context, account, settings, extras, authority, syncResult, "taskList/${localTaskList.id}") {

    val MAX_MULTIGET = 30


    init {
        localCollection = localTaskList
    }

    override fun notificationId() = Constants.NOTIFICATION_TASK_SYNC

    override fun getSyncErrorTitle() = context.getString(R.string.sync_error_tasks, account.name)!!


    override fun prepare(): Boolean {
        collectionURL = HttpUrl.parse(localTaskList.syncId ?: return false) ?: return false
        davCollection = DavCalendar(httpClient, collectionURL)
        return true
    }

    override fun queryCapabilities() {
        davCollection.propfind(0, GetCTag.NAME)
    }

    override fun prepareUpload(resource: LocalResource): RequestBody {
        if (resource is LocalTask) {
            val task = requireNotNull(resource.task)
            Logger.log.log(Level.FINE, "Preparing upload of task ${resource.fileName}", task)

            val os = ByteArrayOutputStream()
            task.write(os)

            return RequestBody.create(
                    DavCalendar.MIME_ICALENDAR,
                    os.toByteArray()
            )
        } else
            throw IllegalArgumentException("resource must be a LocalTask")
    }

    override fun listRemote() {
        // fetch list of remote VTODOs and build hash table to index file name
        val calendar = davCalendar()
        currentDavResource = calendar
        calendar.calendarQuery("VTODO", null, null)

        remoteResources = HashMap<String, DavResource>(davCollection.members.size)
        for (vCard in davCollection.members) {
            val fileName = vCard.fileName()
            Logger.log.fine("Found remote VTODO: $fileName")
            remoteResources[fileName] = vCard
        }

        currentDavResource = null
    }

    override fun downloadRemote() {
        Logger.log.info("Downloading ${toDownload.size} tasks ($MAX_MULTIGET at once)")

        // download new/updated iCalendars from server
        for (bunch in ListUtils.partition(toDownload.toList(), MAX_MULTIGET)) {
            if (Thread.interrupted())
                return

            Logger.log.info("Downloading ${bunch.joinToString(", ")}")

            if (bunch.size == 1) {
                // only one contact, use GET
                val remote = bunch.first()
                currentDavResource = remote

                val body = remote.get("text/calendar")

                // CalDAV servers MUST return ETag on GET [https://tools.ietf.org/html/rfc4791#section-5.3.4]
                val eTag = remote.properties[GetETag.NAME] as GetETag?
                if (eTag == null || eTag.eTag.isNullOrEmpty())
                    throw DavException("Received CalDAV GET response without ETag for ${remote.location}")

                body.charStream().use { reader ->
                    processVTodo(remote.fileName(), eTag.eTag!!, reader)
                }

            } else {
                // multiple contacts, use multi-get
                val calendar = davCalendar()
                currentDavResource = calendar
                calendar.multiget(bunch.map { it.location })

                // process multiget results
                for (remote in davCollection.members) {
                    currentDavResource = remote

                    val eTag = (remote.properties[GetETag.NAME] as GetETag?)?.eTag
                            ?: throw DavException("Received multi-get response without ETag")

                    val calendarData = remote.properties[CalendarData.NAME] as CalendarData?
                    val iCalendar = calendarData?.iCalendar
                            ?: throw DavException("Received multi-get response without task data")

                    processVTodo(remote.fileName(), eTag, StringReader(iCalendar))
                }
            }

            currentDavResource = null
        }
    }


    // helpers

    private fun davCalendar() = davCollection as DavCalendar

    private fun processVTodo(fileName: String, eTag: String, reader: Reader) {
        val tasks: List<Task>
        try {
            tasks = Task.fromReader(reader)
        } catch (e: InvalidCalendarException) {
            Logger.log.log(Level.SEVERE, "Received invalid iCalendar, ignoring", e)
            return
        }

        if (tasks.size == 1) {
            val newData = tasks.first()

            // update local task, if it exists
            val localTask = localResources[fileName] as LocalTask?
            currentLocalResource = localTask
            if (localTask != null) {
                Logger.log.info("Updating $fileName in local tasklist")
                localTask.eTag = eTag
                localTask.update(newData)
                syncResult.stats.numUpdates++
            } else {
                Logger.log.info("Adding $fileName to local task list")
                val newTask = LocalTask(localTaskList, newData, fileName, eTag)
                currentLocalResource = newTask
                newTask.add()
                syncResult.stats.numInserts++
            }
        } else
            Logger.log.severe("Received VCALENDAR with not exactly one VTODO; ignoring $fileName")
    }

}
