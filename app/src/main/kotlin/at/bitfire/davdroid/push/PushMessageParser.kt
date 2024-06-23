/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.push.PushMessage
import java.io.StringReader
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
        parser.setInput(StringReader(message))

        XmlUtils.processTag(parser, PushMessage.NAME) {
            val pushMessage = PushMessage.Factory.create(parser)
            topic = pushMessage.topic
        }

        return topic
    }

}