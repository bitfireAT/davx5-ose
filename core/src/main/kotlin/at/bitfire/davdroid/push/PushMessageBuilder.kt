/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.property.push.ContentEncoding
import at.bitfire.dav4jvm.property.push.WebDAVPush
import at.bitfire.dav4jvm.property.webdav.WebDAV
import java.io.StringWriter
import java.time.Instant

/**
 * Builds WebDAV-Push request message bodies (plain JVM, no Android dependencies).
 */
object PushMessageBuilder {

    /**
     * Builds a WebDAV-Push register request body in XML format.
     *
     * @param endpointUrl The push resource endpoint URL to be registered for push notifications.
     * @param pubKey The optional public key used for encrypting push messages (type: `p256dh`).
     * @param auth The optional authentication secret used for encrypting push messages.
     * @param requestedExpiration The requested expiration time for the push subscription.
     * @return The XML string representation of the WebDAV-Push register request.
     */
    fun buildPushRegister(
        endpointUrl: String,
        pubKey: String?,
        auth: String?,
        requestedExpiration: Instant
    ): String {
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.insertTag(WebDAVPush.PushRegister) {
            serializer.insertTag(WebDAVPush.Subscription) {
                serializer.insertTag(WebDAVPush.WebPushSubscription) {
                    serializer.insertTag(WebDAVPush.PushResource) {
                        text(endpointUrl)
                    }
                    if (pubKey != null && auth != null) {
                        serializer.insertTag(WebDAVPush.ContentEncoding) {
                            text(ContentEncoding.AES128GCM)
                        }
                        serializer.insertTag(WebDAVPush.SubscriptionPublicKey) {
                            attribute(null, "type", "p256dh")
                            text(pubKey)
                        }
                        serializer.insertTag(WebDAVPush.AuthSecret) {
                            text(auth)
                        }
                    }
                }
            }
            serializer.insertTag(WebDAVPush.Trigger) {
                serializer.insertTag(WebDAVPush.ContentUpdate) {
                    serializer.insertTag(WebDAV.Depth) {
                        text("1")
                    }
                }
            }
            serializer.insertTag(WebDAVPush.Expires) {
                text(HttpUtils.formatDate(requestedExpiration))
            }
        }
        serializer.endDocument()
        return writer.toString()
    }

}
