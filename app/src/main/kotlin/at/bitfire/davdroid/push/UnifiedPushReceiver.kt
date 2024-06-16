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
        // parse push notification
        val messageXml = message.toString(Charsets.UTF_8)
        /*val messageXml = "<push-message xmlns=\"DAV:Push\">\n" +
                "  <topic>O7M1nQ7cKkKTKsoS_j6Z3w</topic>\n" +
                "</push-message>"*/

        Logger.log.log(Level.INFO, "Received push message", messageXml)
        var topic: String? = null

        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader(messageXml))
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            XmlUtils.processTag(parser, PushMessage.NAME) {
                val pushMessage = PushMessage.Factory.create(parser)
                topic = pushMessage.topic
            }

            eventType = parser.next()
        }

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