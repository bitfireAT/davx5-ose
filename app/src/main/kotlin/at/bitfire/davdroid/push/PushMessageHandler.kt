/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import androidx.annotation.VisibleForTesting
import at.bitfire.dav4jvm.XmlReader
import at.bitfire.dav4jvm.XmlUtils
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
    private val logger: Logger,
    private val pushSyncManager: PushSyncManager
) {

    /**
     * Processes a WebDAV-Push message and requests synchronization of the affected collection.
     * @param message  the push message to process
     * @param instance UnifiedPush Registration Instance. We use [at.bitfire.davdroid.db.Service.id]
     */
    suspend fun processMessage(message: PushMessage, instance: String) {
        if (!message.decrypted) {
            logger.severe("Received a push message that could not be decrypted.")
            return
        }
        val messageXml = message.content.toString(Charsets.UTF_8)
        logger.log(Level.INFO, "Received push message", messageXml)

        // parse push notification
        val topic = parse(messageXml)
        val serviceId = instance.toLongOrNull()

        // Request synchronization
        pushSyncManager.requestSync(topic, serviceId)
    }

    /**
     * Parses a WebDAV-Push message and returns the `topic` that the message is about.
     * @param message  the push message to parse
     * @return topic of the modified collection, or `null` if the topic couldn't be determined
     */
    @VisibleForTesting
    internal fun parse(message: String): String? {
        var topic: String? = null

        val parser = XmlUtils.newPullParser()
        try {
            parser.setInput(StringReader(message))

            XmlReader(parser).processTag(DavPushMessage.NAME) {
                val pushMessage = DavPushMessage.Factory.create(parser)
                topic = pushMessage.topic?.topic
            }
        } catch (e: XmlPullParserException) {
            logger.log(Level.WARNING, "Couldn't parse push message", e)
        }

        return topic
    }

}