/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import android.provider.CalendarContract
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection.Companion.TYPE_ADDRESSBOOK
import at.bitfire.davdroid.db.Collection.Companion.TYPE_CALENDAR
import at.bitfire.davdroid.db.Collection.Companion.TYPE_WEBCAL
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.repository.PreferenceRepository
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
    lateinit var tasksAppManager: Lazy<TasksAppManager>

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
            val topic = parsePushMessage(messageXml)

            // sync affected collection
            if (topic != null) {
                logger.info("Got push notification for topic $topic")

                // Sync all authorities of account that the collection belongs to
                // Later: only sync affected collection and authorities
                collectionRepository.getSyncableByTopic(topic)?.let { collection ->
                    serviceRepository.get(collection.serviceId)?.let { service ->
                        val account = accountRepository.fromName(service.accountName)
                        val authority = when (collection.type) {
                            TYPE_ADDRESSBOOK -> context.getString(R.string.address_books_authority)
                            TYPE_CALENDAR -> CalendarContract.AUTHORITY
                            TYPE_WEBCAL -> CalendarContract.AUTHORITY
                            else -> null
                        }
                        if (authority == null) {
                            logger.warning("Tried to sync collection ${collection.id} with an unknown type: ${collection.type}")
                            return@let
                        }
                        syncWorkerManager.enqueueOneTime(account, authority, fromPush = true)

                        // If the collection supports tasks, also schedule the tasks authority if any
                        if (collection.supportsVJOURNAL != false || collection.supportsVTODO != false) {
                            tasksAppManager.get().currentProvider()?.let { taskProvider ->
                                syncWorkerManager.enqueueOneTime(account, taskProvider.authority, fromPush = true)
                            }
                        }
                    }
                }

            } else {
                logger.warning("Got push message without topic, syncing all accounts")
                for (account in accountRepository.getAll())
                    syncWorkerManager.enqueueOneTimeAllAuthorities(account, fromPush = true)

            }
        }
    }

}