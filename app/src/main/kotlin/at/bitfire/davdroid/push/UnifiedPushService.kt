/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import at.bitfire.davdroid.db.Collection.Companion.TYPE_ADDRESSBOOK
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.reflect.KMutableProperty
import kotlin.reflect.jvm.isAccessible

/**
 * Entry point for UnifiedPush.
 *
 * Calls [PushRegistrationManager] for most tasks, except incoming push messages,
 * which are handled directly.
 */
@AndroidEntryPoint
class UnifiedPushService : PushService() {

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var collectionRepository: DavCollectionRepository

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @Inject
    lateinit var parsePushMessage: PushMessageParser

    @Inject
    lateinit var pushRegistrationManager: PushRegistrationManager

    @Inject
    lateinit var tasksAppManager: Lazy<TasksAppManager>

    @Inject
    lateinit var syncWorkerManager: SyncWorkerManager


    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val serviceId = instance.toLongOrNull() ?: return
        logger.warning("Got UnifiedPush endpoint for service $serviceId: ${endpoint.url}")

        // register new endpoint at CalDAV/CardDAV servers
        runBlocking(Dispatchers.Default) {
            pushRegistrationManager.processSubscription(serviceId, endpoint)
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        val serviceId = instance.toLongOrNull() ?: return
        logger.warning("UnifiedPush registration failed for service $serviceId: $reason")

        // unregister subscriptions
        runBlocking(Dispatchers.Default) {
            pushRegistrationManager.removeSubscription(serviceId)
        }
    }

    override fun onUnregistered(instance: String) {
        val serviceId = instance.toLongOrNull() ?: return
        logger.warning("UnifiedPush unregistered for service $serviceId")

        runBlocking(Dispatchers.Default) {
            pushRegistrationManager.removeSubscription(serviceId)
        }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        runBlocking(Dispatchers.Default) {
            if (!message.decrypted) {
                logger.severe("Received a push message that could not be decrypted.")
                return@runBlocking
            }
            val messageXml = message.content.toString(Charsets.UTF_8)
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
                        for (syncDataType in syncDataTypes)
                            syncWorkerManager.enqueueOneTime(account, syncDataType, fromPush = true)
                    }
                }

            } else {
                // fallback when no known topic is present (shouldn't happen)
                val service = instance.toLongOrNull()?.let { serviceRepository.get(it) }
                if (service != null) {
                    logger.warning("Got push message without topic and service, syncing all accounts")
                    val account = accountRepository.fromName(service.accountName)
                    syncWorkerManager.enqueueOneTimeAllAuthorities(account, fromPush = true)

                } else {
                    logger.warning("Got push message without topic, syncing all accounts")
                    for (account in accountRepository.getAll())
                        syncWorkerManager.enqueueOneTimeAllAuthorities(account, fromPush = true)
                }
            }
        }
    }


    companion object {

        /**
         * We need to reset PushService::Companion.binder to null before creating a new PushService with a new binder. The
         * current implementation caches the binder, which will always use the first UnifiedPushService that is created during the first
         * test. All following test will fail because the wrong binder is used by PushService.
         *
         * This method resets the binder using reflection, because it's not accessible directly.
         *
         * See https://codeberg.org/UnifiedPush/android-connector/issues/8
         */
        @TestOnly
        fun resetBinder() {
            // requires kotlin-reflection
            val pushServiceClass = PushService::class
            val companionClass = pushServiceClass.nestedClasses.first { it.isCompanion }

            val binderProperty = companionClass.members
                .filterIsInstance(KMutableProperty::class.java)
                .first { it.name.contains("binder") }
            binderProperty.isAccessible = true
            val binderSetter = binderProperty.setter

            binderSetter.call(companionClass, null)
        }

    }

}