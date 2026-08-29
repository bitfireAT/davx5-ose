/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.push.PushRegister
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.unifiedpush.android.connector.data.PublicKeySet
import org.unifiedpush.android.connector.data.PushEndpoint
import java.io.StringReader
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
class PushRegistrationManagerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var pushRegistrationManager: PushRegistrationManager

    private val expiration = Instant.parse("2025-01-01T00:00:00Z")

    private fun parsePushRegister(xml: String): PushRegister {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader(xml))
        parser.nextTag()   // move to <push-register>
        return PushRegister.Factory.create(parser)
    }

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testBuildPushRegisterBody_PubKeySet_BodyCorrectlyConstructed() {
        val endpoint = PushEndpoint(
            url = "https://up.example.net/abc",
            pubKeySet = PublicKeySet(
                pubKey = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
                auth = "BTBZMqHH6r4Tts7J_aSIgg"
            ),
            temporary = false
        )

        val result = parsePushRegister(pushRegistrationManager.buildPushRegisterBody(endpoint, expiration))

        val subscription = result.subscription?.webPushSubscription
        assertNotNull(subscription)
        assertEquals("https://up.example.net/abc", subscription?.pushResource?.uri?.toString())
        assertEquals("aes128gcm", subscription?.contentEncoding?.encoding)
        assertEquals("p256dh", subscription?.subscriptionPublicKey?.type)
        assertEquals(
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
            subscription?.subscriptionPublicKey?.key
        )
        assertEquals("BTBZMqHH6r4Tts7J_aSIgg", subscription?.authSecret?.secret)

        val contentUpdate = result.trigger?.contentUpdate
        assertNotNull(contentUpdate)
        assertEquals(1, contentUpdate?.depth?.depth)
    }

}
