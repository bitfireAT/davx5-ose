/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import at.bitfire.dav4jvm.DavCollection
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.property.push.AuthSecret
import at.bitfire.dav4jvm.property.push.PushRegister
import at.bitfire.dav4jvm.property.push.PushResource
import at.bitfire.dav4jvm.property.push.Subscription
import at.bitfire.dav4jvm.property.push.SubscriptionPublicKey
import at.bitfire.dav4jvm.property.push.WebPushSubscription
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.RequestBody.Companion.toRequestBody
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

class PushRegistrationManager @Inject constructor(
    private val accountRepository: AccountRepository,
    private val collectionRepository: DavCollectionRepository,
    @ApplicationContext private val context: Context,
    private val httpClientBuilder: Provider<HttpClient.Builder>,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository
) {

    fun update() {
        for (service in serviceRepository.getAll())
            update(service.id)
    }

    fun update(serviceId: Long) {
        val service = serviceRepository.get(serviceId) ?: return
        val vapid = collectionRepository.getVapidKey(serviceId)

        if (vapid != null)
            try {
                UnifiedPush.register(context, serviceId.toString(), service.accountName, vapid)
            } catch (e: UnifiedPush.VapidNotValidException) {
                logger.log(Level.WARNING, "Couldn't register invalid VAPID key for service $serviceId", e)
            }
        else
            UnifiedPush.unregister(context, serviceId.toString())
    }

    fun registerSubscription(serviceId: Long, endpoint: PushEndpoint) {
        val service = serviceRepository.get(serviceId) ?: return

        val collectionsToRegister = collectionRepository.getPushCapableAndSyncable(serviceId)
        if (collectionsToRegister.isEmpty())
            return

        val account = accountRepository.fromName(service.accountName)
        httpClientBuilder.get()
            .fromAccount(account)
            .build()
            .use { httpClient ->
                for (collection in collectionsToRegister)
                    try {
                        val expires = collection.pushSubscriptionExpires
                        // calculate next run time, but use the duplicate interval for safety (times are not exact)
                        val nextRun = Instant.now() + Duration.ofDays(2 * PushRegistrationWorkerManager.INTERVAL_DAYS)
                        if (expires != null && expires >= nextRun.epochSecond)
                            logger.fine("Push subscription for ${collection.url} is still valid until ${collection.pushSubscriptionExpires}")
                        else {
                            // no existing subscription or expiring soon
                            logger.fine("Registering push subscription for ${collection.url}")
                            registerSubscription(httpClient, collection, endpoint)
                        }
                    } catch (e: Exception) {
                        logger.log(Level.WARNING, "Couldn't register subscription at CalDAV/CardDAV server", e)
                    }
            }
    }

    private fun registerSubscription(httpClient: HttpClient, collection: Collection, endpoint: PushEndpoint) {
        // requested expiration time: 3 days
        val requestedExpiration = Instant.now() + Duration.ofDays(3)

        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.insertTag(PushRegister.NAME) {
            serializer.insertTag(Subscription.NAME) {
                // subscription URL
                serializer.insertTag(WebPushSubscription.NAME) {
                    serializer.insertTag(PushResource.NAME) {
                        text(endpoint.url)
                    }
                    endpoint.pubKeySet?.let { pubKeySet ->
                        serializer.insertTag(SubscriptionPublicKey.NAME) {
                            attribute(null, "type", "p256dh")
                            text(pubKeySet.pubKey)
                        }
                        serializer.insertTag(AuthSecret.NAME) {
                            text(pubKeySet.auth)
                        }
                    }
                }
            }
            // requested expiration
            serializer.insertTag(PushRegister.EXPIRES) {
                text(HttpUtils.formatDate(requestedExpiration))
            }
        }
        serializer.endDocument()

        val xml = writer.toString().toRequestBody(DavResource.MIME_XML)
        DavCollection(httpClient.okHttpClient, collection.url).post(xml) { response ->
            if (response.isSuccessful) {
                // update subscription URL and expiration in DB
                val subscriptionUrl = response.header("Location")
                val expires = response.header("Expires")?.let { expiresDate ->
                    HttpUtils.parseDate(expiresDate)
                } ?: requestedExpiration
                collectionRepository.updatePushSubscription(
                    id = collection.id,
                    subscriptionUrl = subscriptionUrl,
                    expires = expires?.epochSecond
                )
            } else
                logger.warning("Couldn't register push for ${collection.url}: $response")
        }
    }

    fun unregisterSubscription(serviceId: Long) {
        // TODO
    }

}