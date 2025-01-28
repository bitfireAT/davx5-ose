/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.accounts.Account
import android.content.Context
import at.bitfire.davdroid.db.Collection.Companion.TYPE_ADDRESSBOOK
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.repository.PreferenceRepository
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.resource.LocalCalendarStore
import at.bitfire.davdroid.resource.LocalDataStore
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.MessagingReceiver
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

@AndroidEntryPoint
class UnifiedPushReceiver: MessagingReceiver() {

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var collectionRepository: DavCollectionRepository

    @Inject
    lateinit var localAddressBookStore: Lazy<LocalAddressBookStore>

    @Inject
    lateinit var localCalendarStore: Lazy<LocalCalendarStore>

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @Inject
    lateinit var preferenceRepository: PreferenceRepository

    @Inject
    lateinit var parsePushMessage: PushMessageParser

    @Inject
    lateinit var pushRegistrationWorkerManager: PushRegistrationWorkerManager

    @Inject
    lateinit var syncWorkerManager: SyncWorkerManager

    @Inject
    lateinit var tasksAppManager: Lazy<TasksAppManager>


    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        // remember new endpoint
        preferenceRepository.unifiedPushEndpoint(endpoint)

        // register new endpoint at CalDAV/CardDAV servers
        pushRegistrationWorkerManager.updatePeriodicWorker()
    }

    override fun onUnregistered(context: Context, instance: String) {
        // reset known endpoint
        preferenceRepository.unifiedPushEndpoint(null)
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        CoroutineScope(Dispatchers.Default).launch {
            val messageXml = message.toString(Charsets.UTF_8)
            logger.log(Level.INFO, "Received push message", messageXml)

            // parse push notification
            val pushMessage = parsePushMessage(messageXml)

            // sync affected collection
            if (pushMessage.topic != null) {
                logger.info("Got push notification for topic ${pushMessage.topic} with sync-token=${pushMessage.syncToken}")

                // Sync all authorities of account that the collection belongs to
                // Later: only sync affected collection and authorities
                collectionRepository.getSyncableByTopic(pushMessage.topic)?.let { collection ->
                    serviceRepository.get(collection.serviceId)?.let { service ->
                        val syncDataTypes = mutableSetOf<SyncDataType>()
                        // If the type is an address book, add the contacts type
                        if (collection.type == TYPE_ADDRESSBOOK)
                            syncDataTypes += SyncDataType.CONTACTS

                        // If the collection supports events, add the events type
                        if (collection.supportsVEVENT != false)
                            syncDataTypes += SyncDataType.EVENTS

                        // If the collection supports tasks, make sure there's a provider installed,
                        // and add the tasks type
                        if (collection.supportsVJOURNAL != false || collection.supportsVTODO != false)
                            if (tasksAppManager.get().currentProvider() != null)
                                syncDataTypes += SyncDataType.TASKS

                        // Schedule sync for all the types identified
                        val account = accountRepository.fromName(service.accountName)
                        for (syncDataType in syncDataTypes) {
                            val localSyncToken = lastSyncToken(account, syncDataType, collection.id)
                            if (localSyncToken == pushMessage.syncToken)
                                logger.fine("Collection $collection already up-to-date ($localSyncToken), ignoring push message")
                            else
                                syncWorkerManager.enqueueOneTime(account, syncDataType, fromPush = true)
                        }
                    }
                }

            } else {
                logger.warning("Got push message without topic, syncing all accounts")
                for (account in accountRepository.getAll())
                    syncWorkerManager.enqueueOneTimeAllAuthorities(account = account, fromPush = true)

            }
        }
    }

    fun lastSyncToken(account: Account, dataType: SyncDataType, collectionId: Long): String? {
        val dataStore: LocalDataStore<*>? = when (dataType) {
            SyncDataType.CONTACTS -> localAddressBookStore.get()
            SyncDataType.EVENTS -> localCalendarStore.get()
            SyncDataType.TASKS -> tasksAppManager.get().getDataStore()
        }

        val provider = TODO("Add LocalDataStore.acquireProvider()")
        val localCollection = dataStore?.getByLocalId(account, provider, collectionId)
        return localCollection?.lastSyncState?.value.takeIf {
            localCollection?.lastSyncState?.type == SyncState.Type.SYNC_TOKEN
        }
    }

}