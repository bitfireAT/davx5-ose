/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import androidx.annotation.VisibleForTesting
import at.bitfire.dav4jvm.XmlReader
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.push.WebDAVPush
import at.bitfire.davdroid.db.Collection.Companion.TYPE_ADDRESSBOOK
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
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
    private val serviceRepository: DavServiceRepository,
    private val syncWorkerManager: SyncWorkerManager,
    private val tasksAppManager: Lazy<TasksAppManager>
) {

    suspend fun processMessage(message: PushMessage, instance: String) {
        if (!message.decrypted) {
            logger.severe("Received a push message that could not be decrypted.")
            return
        }
        val messageXml = message.content.toString(Charsets.UTF_8)
        logger.log(Level.INFO, "Received push message", messageXml)

        // parse push notification
        val topic = parse(messageXml)

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
            val service = instance.toLongOrNull()?.let { serviceRepository.getBlocking(it) }
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

    /**
     * Parses a WebDAV-Push message and returns the `topic` that the message is about.
     *
     * @return topic of the modified collection, or `null` if the topic couldn't be determined
     */
    @VisibleForTesting
    internal fun parse(message: String): String? {
        var topic: String? = null

        val parser = XmlUtils.newPullParser()
        try {
            parser.setInput(StringReader(message))

            XmlReader(parser).processTag(WebDAVPush.PushMessage) {
                val pushMessage = DavPushMessage.Factory.create(parser)
                topic = pushMessage.topic?.topic
            }
        } catch (e: XmlPullParserException) {
            logger.log(Level.WARNING, "Couldn't parse push message", e)
        }

        return topic
    }

}