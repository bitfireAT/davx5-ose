/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.push.PushMessage
import at.bitfire.davdroid.log.Logger
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader
import java.util.logging.Level
import javax.inject.Inject

class PushMessageParser @Inject constructor() {

    /**
     * Parses a WebDAV-Push message and returns the `topic` that the message is about.
     *
     * @return topic of the modified collection, or `null` if the topic couldn't be determined
     */
    operator fun invoke(message: String): String? {
        var topic: String? = null

        val parser = XmlUtils.newPullParser()
        try {
            parser.setInput(StringReader(message))

            XmlUtils.processTag(parser, PushMessage.NAME) {
                val pushMessage = PushMessage.Factory.create(parser)
                topic = pushMessage.topic
            }
        } catch (e: XmlPullParserException) {
            Logger.log.log(Level.WARNING, "Couldn't parse push message", e)
        }

        return topic
    }

}