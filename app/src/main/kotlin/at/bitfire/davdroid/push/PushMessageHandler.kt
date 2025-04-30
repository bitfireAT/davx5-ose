/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.accounts.Account
import androidx.annotation.VisibleForTesting
import at.bitfire.dav4jvm.XmlReader
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Collection.Companion.TYPE_ADDRESSBOOK
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.resource.LocalCalendarStore
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.davdroid.util.trimToNull
import dagger.Lazy
import org.unifiedpush.android.connector.data.PushMessage
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import at.bitfire.dav4jvm.property.push.PushMessage as DavPushMessage

/**
 * Handles incoming WebDAV-Push messages.
 */
class PushMessageHandler @Inject constructor(
    private val accountRepository: AccountRepository,
    private val collectionRepository: DavCollectionRepository,
    private val logger: Logger,
    private val localCalendarStore: Lazy<LocalCalendarStore>,
    private val localAddressBookStore: Lazy<LocalAddressBookStore>,
    private val serviceRepository: DavServiceRepository,
    private val syncWorkerManager: SyncWorkerManager,
    private val tasksAppManager: Lazy<TasksAppManager>
) {

    /**
     * Compares incoming push message sync token with locally remembered sync token and
     * enqueues an account wide sync if the sync token has changed. Will enqueue all accounts
     * if no topic is found in the message.
     *
     * @param message The message to be processed
     * @param instance The registration instance. We are using [at.bitfire.davdroid.db.Service.id]
     */
    suspend fun processMessage(message: PushMessage, instance: String) {
        if (!message.decrypted) {
            logger.severe("Received a push message that could not be decrypted.")
            return
        }
        val messageXml = message.content.toString(Charsets.UTF_8)
        logger.log(Level.INFO, "Received push message", messageXml)

        // Parse push notification
        val pushMessage = parse(messageXml)

        // Sync all accounts when no known topic is present (shouldn't happen)
        val topic = pushMessage?.topic?.topic
        if (topic == null) {
            val service = instance.toLongOrNull()?.let { serviceRepository.get(it) }
            if (service != null) {
                logger.warning("Got push message without topic, syncing all accounts")
                val account = accountRepository.fromName(service.accountName)
                syncWorkerManager.enqueueOneTimeAllAuthorities(account, fromPush = true)
            } else {
                logger.warning("Got push message without topic and service, syncing all accounts")
                for (account in accountRepository.getAll())
                    syncWorkerManager.enqueueOneTimeAllAuthorities(account, fromPush = true)
            }
            return
        }

        // Find DB collection from topic
        logger.info("Got push notification for topic $topic")
        val dbCollection = collectionRepository.getSyncableByTopic(topic)
        if (dbCollection == null) {
            logger.info("Skipping sync: No sync-enabled collection with topic $topic")
            return
        }

        // Find account from DB collection
        val account = serviceRepository.getAsync(dbCollection.serviceId)?.let { service ->
            accountRepository.fromName(service.accountName)
        } ?: return

        // Check sync token is present
        val newSyncToken = pushMessage.contentUpdate?.syncToken?.token?.trimToNull()
        if (newSyncToken == null) {
            logger.info("No sync token in push message. Skipping sync")
            return
        }

        // Sync all data types of account which the collection belongs to
        // Future: only sync affected collection
        for (syncDataType in syncDataTypes(dbCollection)) {

            // Find old sync token
            val oldSyncToken = getSavedSyncToken(account, syncDataType, dbCollection.id)?.trimToNull()
            if (oldSyncToken == null) {
                logger.info("No local sync token for collection #${dbCollection.id}. Skipping sync")
                continue
            }

            // Check whether sync token has changed
            if (oldSyncToken == newSyncToken) {
                logger.info("Sync token has not changed. Skipping sync")
                continue
            }

            // Enqueue sync
            logger.info("Sync token changed! Enqueuing one-time sync")
            syncWorkerManager.enqueueOneTime(account, syncDataType, fromPush = true)
        }
    }


    // helpers

    /**
     * Parses a WebDAV-Push message and returns it as [DavPushMessage].
     *
     * @param message WebDAV-Push message to be parsed
     * @return [DavPushMessage]
     */
    @VisibleForTesting
    internal fun parse(message: String): DavPushMessage? {
        var pushMessage: DavPushMessage? = null

        val parser = XmlUtils.newPullParser()
        try {
            parser.setInput(StringReader(message))

            XmlReader(parser).processTag(DavPushMessage.NAME) {
                pushMessage = DavPushMessage.Factory.create(parser)
            }
        } catch (e: XmlPullParserException) {
            logger.log(Level.WARNING, "Couldn't parse push message", e)
        }

        return pushMessage
    }

    /**
     * Determines sync data types of given collection
     *
     * @param dbCollection Collection to find sync data types for
     * @return sync data types for given collection
     */
    @VisibleForTesting
    internal fun syncDataTypes(dbCollection: Collection): Set<SyncDataType> = buildSet {
        // If the type is an address book, add the contacts type
        if (dbCollection.type == TYPE_ADDRESSBOOK)
            add(SyncDataType.CONTACTS)

        // If the collection supports events, add the events type
        if (dbCollection.supportsVEVENT != false)
            add(SyncDataType.EVENTS)

        // If the collection supports tasks, make sure there's a provider installed,
        // and add the tasks type
        if (dbCollection.supportsVJOURNAL != false || dbCollection.supportsVTODO != false)
            if (tasksAppManager.get().currentProvider() != null)
                add(SyncDataType.TASKS)
    }

    /**
     * Retrieves the sync token stored in local collection corresponding to given remote/DB collection.
     *
     * @param account Account where the local collection is stored
     * @param syncDataType To determine the content provider
     * @param dbCollectionId ID of remote collection
     * @return sync token
     */
    @VisibleForTesting
    internal fun getSavedSyncToken(account: Account, syncDataType: SyncDataType, dbCollectionId: Long): String? {
        val localDataStore = when (syncDataType) {
            SyncDataType.CONTACTS -> localAddressBookStore.get()
            SyncDataType.EVENTS -> localCalendarStore.get()
            SyncDataType.TASKS -> tasksAppManager.get().getDataStore()
        }
        return localDataStore?.acquireContentProvider()?.use { provider ->
            localDataStore.getByDbCollectionId(account, provider, dbCollectionId)
                ?.lastSyncState
                ?.takeIf { it.type == SyncState.Type.SYNC_TOKEN }
                ?.value
        }
    }

}