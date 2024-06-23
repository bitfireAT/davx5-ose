/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.push.PushMessage
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.PreferenceRepository
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import org.unifiedpush.android.connector.MessagingReceiver
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.logging.Level
import javax.inject.Inject

@AndroidEntryPoint
class UnifiedPushReceiver: MessagingReceiver() {

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var collectionRepository: DavCollectionRepository

    @Inject
    lateinit var preferenceRepository: PreferenceRepository

    @Inject
    lateinit var parsePushMessage: PushMessageParser


    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        // remember new endpoint
        preferenceRepository.unifiedPushEndpoint(endpoint)

        // register new endpoint at CalDAV/CardDAV servers
        PushRegistrationWorker.enqueue(context)
    }

    override fun onUnregistered(context: Context, instance: String) {
        // reset known endpoint
        preferenceRepository.unifiedPushEndpoint(null)
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        val messageXml = message.toString(Charsets.UTF_8)
        Logger.log.log(Level.INFO, "Received push message", messageXml)

        // parse push notification
        val topic = parsePushMessage(messageXml)

        // sync affected collection
        if (topic != null) {
            Logger.log.info("Got push notification for topic $topic")

            // TODO fetch collection by topic
            //collectionRepository.getByTopic()

        } else {
            Logger.log.warning("Got push message without topic, syncing all accounts")
            for (account in accountRepository.getAll())
                OneTimeSyncWorker.enqueueAllAuthorities(context, account)

        }
    }

}