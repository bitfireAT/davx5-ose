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
import at.bitfire.dav4android.property.SyncToken
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.LocalTask
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.Task
import okhttp3.HttpUrl
import okhttp3.RequestBody
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.util.*
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles tasks (VTODO)
 */
class TasksSyncManager(
        context: Context,
        settings: ISettings,
        account: Account,
        accountSettings: AccountSettings,
        extras: Bundle,
        authority: String,
        syncResult: SyncResult,
        localCollection: LocalTaskList
): BaseDavSyncManager<LocalTask, LocalTaskList, DavCalendar>(context, settings, account, accountSettings, extras, authority, syncResult, localCollection) {

    companion object {
        const val MULTIGET_MAX_RESOURCES = 30
    }


    override fun prepare(): Boolean {
        if (!super.prepare())
            return false

        val url = localCollection.syncId ?: return false
        collectionURL = HttpUrl.parse(url) ?: return false
        davCollection = DavCalendar(httpClient.okHttpClient, collectionURL)

        return true
    }

    override fun queryCapabilities() {
        useRemoteCollection { it.propfind(0, GetCTag.NAME, SyncToken.NAME) }
    }

    override fun syncAlgorithm() = SyncAlgorithm.PROPFIND_REPORT

    override fun prepareUpload(resource: LocalTask): RequestBody = useLocal(resource, {
        val task = requireNotNull(resource.task)
        Logger.log.log(Level.FINE, "Preparing upload of task ${resource.fileName}", task)

        val os = ByteArrayOutputStream()
        task.write(os)

        RequestBody.create(
                DavCalendar.MIME_ICALENDAR_UTF8,
                os.toByteArray()
        )
    })

    override fun listAllRemote() = useRemoteCollection { remote ->
        remote.calendarQuery("VTODO", null, null)

        val result = LinkedHashMap<String, DavResource>(remote.members.size)
        for (vCard in remote.members) {
            val fileName = vCard.fileName()
            Logger.log.fine("Found remote VTODO: $fileName")
            result[fileName] = vCard
        }
        result
    }

    override fun processRemoteChanges(changes: RemoteChanges) {
        for (name in changes.deleted) {
            localCollection.findByName(name)?.let {
                Logger.log.info("Deleting local task $name")
                useLocal(it, { it.delete() })
                syncResult.stats.numDeletes++
            }
        }

        val toDownload = changes.updated.map { it.location }
        Logger.log.info("Downloading ${toDownload.size} resources (${MULTIGET_MAX_RESOURCES} at once)")

        for (bunch in toDownload.chunked(MULTIGET_MAX_RESOURCES)) {
            if (bunch.size == 1)
                // only one contact, use GET
                useRemote(DavResource(httpClient.okHttpClient, bunch.first()), { remote ->
                    val body = remote.get(DavCalendar.MIME_ICALENDAR.toString())

                    // CalDAV servers MUST return ETag on GET [https://tools.ietf.org/html/rfc4791#section-5.3.4]
                    val eTag = remote.properties[GetETag::class.java]?.eTag
                            ?: throw DavException("Received CalDAV GET response without ETag for ${remote.location}")

                    body.charStream().use { reader ->
                        processVTodo(remote.fileName(), eTag, reader)
                    }
                })
            else {
                // multiple contacts, use multi-get
                davCollection.multiget(bunch)

                // process multiget results
                for (remote in davCollection.members) {
                    val eTag = remote.properties[GetETag::class.java]?.eTag
                            ?: throw DavException("Received multi-get response without ETag")

                    val calendarData = remote.properties[CalendarData::class.java]
                    val iCalendar = calendarData?.iCalendar
                            ?: throw DavException("Received multi-get response without task data")

                    processVTodo(remote.fileName(), eTag, StringReader(iCalendar))
                }
            }

            abortIfCancelled()
        }
    }

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
            useLocal(localCollection.findByName(fileName), { local ->
                if (local != null) {
                    Logger.log.info("Updating $fileName in local tasklist")
                    local.eTag = eTag
                    local.update(newData)
                    syncResult.stats.numUpdates++
                } else {
                    Logger.log.info("Adding $fileName to local task list")
                    val newTask = LocalTask(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                    newTask.add()
                    syncResult.stats.numInserts++
                }
            })
        } else
            Logger.log.info("Received VCALENDAR with not exactly one VTODO; ignoring $fileName")
    }

}