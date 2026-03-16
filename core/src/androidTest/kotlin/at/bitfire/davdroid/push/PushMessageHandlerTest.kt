/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class PushMessageHandlerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var handler: PushMessageHandler

    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test
    fun testParse_InvalidXml() {
        Assert.assertNull(handler.parse("Non-XML content"))
    }

    @Test
    fun testParse_WithXmlDeclAndTopic() {
        val topic = handler.parse(
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<P:push-message xmlns:D=\"DAV:\" xmlns:P=\"https://bitfire.at/webdav-push\">" +
            "  <P:topic>O7M1nQ7cKkKTKsoS_j6Z3w</P:topic>" +
            "</P:push-message>"
        )
        Assert.assertEquals("O7M1nQ7cKkKTKsoS_j6Z3w", topic)
    }

}