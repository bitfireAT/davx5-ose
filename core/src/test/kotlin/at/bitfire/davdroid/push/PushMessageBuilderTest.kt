/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PushMessageBuilderTest {

    private val expiration = Instant.parse("2025-01-01T00:00:00Z")

    @Test
    fun `buildPushRegister() with pubKeySet builds full encrypted subscription`() {
        val result = PushMessageBuilder.buildPushRegister(
            endpointUrl = "https://up.example.net/abc",
            pubKey = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
            auth = "BTBZMqHH6r4Tts7J_aSIgg",
            requestedExpiration = expiration
        )

        assertEquals(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<n1:push-register xmlns:n1="https://bitfire.at/webdav-push">""" +
                    """<n1:subscription><n1:web-push-subscription>""" +
                    """<n1:push-resource>https://up.example.net/abc</n1:push-resource>""" +
                    """<n1:content-encoding>aes128gcm</n1:content-encoding>""" +
                    """<n1:subscription-public-key type="p256dh">BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4</n1:subscription-public-key>""" +
                    """<n1:auth-secret>BTBZMqHH6r4Tts7J_aSIgg</n1:auth-secret>""" +
                    """</n1:web-push-subscription></n1:subscription>""" +
                    """<n1:trigger><n1:content-update><n2:depth xmlns:n2="DAV:">1</n2:depth></n1:content-update></n1:trigger>""" +
                    """<n1:expires>Wed, 01 Jan 2025 00:00:00 GMT</n1:expires>""" +
                    """</n1:push-register>""",
            result
        )
    }

    @Test
    fun `buildPushRegister() without pubKeySet omits encryption elements`() {
        val result = PushMessageBuilder.buildPushRegister(
            endpointUrl = "https://up.example.net/abc",
            pubKey = null, auth = null,
            requestedExpiration = expiration
        )

        assertEquals(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<n1:push-register xmlns:n1="https://bitfire.at/webdav-push">""" +
                    """<n1:subscription><n1:web-push-subscription>""" +
                    """<n1:push-resource>https://up.example.net/abc</n1:push-resource>""" +
                    """</n1:web-push-subscription></n1:subscription>""" +
                    """<n1:trigger><n1:content-update><n2:depth xmlns:n2="DAV:">1</n2:depth></n1:content-update></n1:trigger>""" +
                    """<n1:expires>Wed, 01 Jan 2025 00:00:00 GMT</n1:expires>""" +
                    """</n1:push-register>""",
            result
        )
    }

}
