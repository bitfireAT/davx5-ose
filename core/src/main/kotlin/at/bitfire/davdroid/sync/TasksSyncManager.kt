/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.LocalTask
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.resource.remote.CalDavCollection
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.davdroid.sync.mapping.ResourceMapper
import at.bitfire.davdroid.sync.mapping.TaskMapper
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.synctools.exception.InvalidResourceException
import at.bitfire.synctools.icalendar.AssociatedTasks
import at.bitfire.synctools.icalendar.CalendarUidSplitter
import at.bitfire.synctools.icalendar.ICalendarParser
import at.bitfire.synctools.mapping.tasks.DmfsTaskBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VToDo
import java.io.Reader
import java.io.StringReader
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles tasks (VTODO)
 */
class TasksSyncManager @AssistedInject constructor(
    @Assisted accountId: AccountId,
    @Assisted httpClient: HttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted override val localCollection: LocalTaskList,
    @Assisted collectionInfo: Collection,
    @Assisted override val remoteCollection: CalDavCollection,
    @Assisted resync: ResyncType?,
    @Assisted settings: SyncSettings,
    taskMapperFactory: TaskMapper.Factory
) : SyncManager<LocalTask>(
    accountId,
    httpClient,
    SyncDataType.TASKS,
    syncResult,
    collectionInfo,
    resync,
    settings
) {

    @AssistedFactory
    interface Factory {
        fun tasksSyncManager(
            accountId: AccountId,
            httpClient: HttpClient,
            syncResult: SyncResult,
            localCollection: LocalTaskList,
            collectionInfo: Collection,
            remoteCollection: CalDavCollection,
            resync: ResyncType?,
            settings: SyncSettings
        ): TasksSyncManager
    }

    override val resourceMapper: ResourceMapper<LocalTask> = taskMapperFactory.create(localCollection)


    override fun syncAlgorithm(capabilities: WebDavCollection.Capabilities) = SyncAlgorithm.PROPFIND_REPORT

    override suspend fun processDownload(result: WebDavCollection.MultiGetItem) {
        result.url.withExceptionContext {
            val fileName = result.url.lastSegment
            try {
                processVTodo(fileName, result.eTag, StringReader(result.content))
            } catch (e: InvalidResourceException) {
                logger.log(Level.WARNING, "Error while processing VTODO", e)
                notifyInvalidResource(e, fileName)
            }
        }
    }

    override suspend fun postProcess() {
        val touched = localCollection.dmfsTaskList.touchRelations()
        logger.info("Touched $touched relations")
    }


    // helpers

    private suspend fun processVTodo(fileName: String, eTag: String, reader: Reader) {
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
            local.withExceptionContext {
                logger.info("Updating $fileName in local task list: $task")
                local.update(dmfsTask)
            }
        } else {
            logger.info("Adding $fileName to local task list: $task")
            localCollection.add(dmfsTask)
        }
    }

}