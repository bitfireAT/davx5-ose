/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.logging.Logger

class PushMessageParserTest {

    private val parse = PushMessageParser(logger = Logger.getGlobal())

    @Test
    fun testInvalidXml() {
        assertNull(parse("Non-XML content"))
    }

    @Test
    fun testWithXmlDeclAndTopic() {
        val topic = parse(
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<P:push-message xmlns:D=\"DAV:\" xmlns:P=\"https://bitfire.at/webdav-push\">" +
            "  <P:topic>O7M1nQ7cKkKTKsoS_j6Z3w</P:topic>" +
            "</P:push-message>"
        )
        assertEquals("O7M1nQ7cKkKTKsoS_j6Z3w", topic)
    }

}