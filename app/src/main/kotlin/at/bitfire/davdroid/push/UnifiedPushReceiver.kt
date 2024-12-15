/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.repository.PreferenceRepository
import at.bitfire.davdroid.resource.LocalDataStore
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
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
                        for (authority in syncWorkerManager.syncAuthorities()) {
                            // TODO: get LocalDataStore according to authority and service (i.e. LocalAddressBookStore if authority is contacts, etc.)
                            val dataStore: LocalDataStore<*>? = TODO()

                            val localCollection = dataStore?.getByLocalId(collection.id)
                            val lastSyncedSyncToken = localCollection?.lastSyncState?.value.takeIf {
                                localCollection?.lastSyncState?.type == SyncState.Type.SYNC_TOKEN
                            }
                            if (lastSyncedSyncToken == pushMessage.syncToken) {
                                logger.fine("Collection $collection is already up-to-date, ignoring push message")
                                continue
                            }

                            val account = accountRepository.fromName(service.accountName)
                            syncWorkerManager.enqueueOneTime(account = account, authority = authority, fromPush = true)
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

}