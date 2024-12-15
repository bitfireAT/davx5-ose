/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import at.bitfire.dav4jvm.XmlReader
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.push.PushMessage
import at.bitfire.dav4jvm.property.push.Topic
import at.bitfire.dav4jvm.property.webdav.SyncToken
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class PushMessageParser @Inject constructor(
    private val logger: Logger
) {

    data class PushMessageBody(
        val topic: String?,
        val syncToken: String?
    )

    /**
     * Parses a WebDAV-Push message and returns the `topic` that the message is about.
     *
     * @return topic of the modified collection, or `null` if the topic couldn't be determined
     */
    operator fun invoke(message: String): PushMessageBody {
        var topic: String? = null
        var syncToken: String? = null

        val parser = XmlUtils.newPullParser()
        try {
            parser.setInput(StringReader(message))

            XmlReader(parser).processTag(PushMessage.NAME) {
                val pushMessage = PushMessage.Factory.create(parser)
                val properties = pushMessage.propStat?.properties ?: return@processTag
                topic = properties.filterIsInstance<Topic>().firstOrNull()?.topic
                syncToken = properties.filterIsInstance<SyncToken>().firstOrNull()?.token
            }
        } catch (e: XmlPullParserException) {
            logger.log(Level.WARNING, "Couldn't parse push message", e)
        }

        return PushMessageBody(
            topic = topic,
            syncToken = syncToken
        )
    }

}