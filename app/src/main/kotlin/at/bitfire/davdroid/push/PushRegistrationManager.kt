/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import at.bitfire.dav4jvm.DavCollection
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.property.push.AuthSecret
import at.bitfire.dav4jvm.property.push.PushRegister
import at.bitfire.dav4jvm.property.push.PushResource
import at.bitfire.dav4jvm.property.push.Subscription
import at.bitfire.dav4jvm.property.push.SubscriptionPublicKey
import at.bitfire.dav4jvm.property.push.WebPushSubscription
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

class PushRegistrationManager @Inject constructor(
    private val accountRepository: Lazy<AccountRepository>,
    private val collectionRepository: DavCollectionRepository,
    @ApplicationContext private val context: Context,
    private val httpClientBuilder: Provider<HttpClient.Builder>,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository
) {

    /**
     * Updates all push registrations and subscriptions so that if Push is available, it's up-to-date and
     * working for all database services.
     *
     * Also makes sure that the [PushRegistrationWorker] is enabled if there's a Push-enabled collection.
     */
    suspend fun update() = withContext(dispatcher) {
        for (service in serviceRepository.getAll())
            updateService(service.id)

        updatePeriodicWorker()
    }

    /**
     * Same as [update], but for a specific database service.
     */
    suspend fun update(serviceId: Long) {
        updateService(serviceId)
        updatePeriodicWorker()
    }

    private suspend fun updateService(serviceId: Long) = withContext(dispatcher) {
        val service = serviceRepository.get(serviceId) ?: return@withContext
        val vapid = collectionRepository.getVapidKey(serviceId)

        if (vapid != null)
            try {
                UnifiedPush.register(context, serviceId.toString(), service.accountName, vapid)
            } catch (e: UnifiedPush.VapidNotValidException) {
                logger.log(Level.WARNING, "Couldn't register invalid VAPID key for service $serviceId", e)
            }
        else
            UnifiedPush.unregister(context, serviceId.toString())

        // UnifiedPush has now been called. It will do its work and then call back to UnifiedPushService, which
        // will then call processSubscription or removeSubscription.
    }


    /**
     * Called when a subscription (endpoint) is available for the given service.
     *
     * Uses the subscription to subscribe to syncable collections, and then unsubscribes from non-syncable collections.
     */
    internal suspend fun processSubscription(serviceId: Long, endpoint: PushEndpoint) = withContext(dispatcher) {
        val service = serviceRepository.get(serviceId) ?: return@withContext

        subscribeSyncable(service, endpoint)
        unsubscribeNotSyncable(service)
    }

    private suspend fun subscribeSyncable(service: Service, endpoint: PushEndpoint) {
        val subscribeTo = collectionRepository.getPushCapableAndSyncable(service.id)
        if (subscribeTo.isEmpty())
            return

        val account = accountRepository.get().fromName(service.accountName)
        httpClientBuilder.get()
            .fromAccount(account)
            .build()
            .use { httpClient ->
                for (collection in subscribeTo)
                    try {
                        val expires = collection.pushSubscriptionExpires
                        // calculate next run time, but use the duplicate interval for safety (times are not exact)
                        val nextRun = Instant.now() + Duration.ofDays(2 * WORKER_INTERVAL_DAYS)
                        if (expires != null && expires >= nextRun.epochSecond)
                            logger.fine("Push subscription for ${collection.url} is still valid until ${collection.pushSubscriptionExpires}")
                        else {
                            // no existing subscription or expiring soon
                            logger.fine("Registering push subscription for ${collection.url}")
                            subscribe(httpClient, collection, endpoint)
                        }
                    } catch (e: Exception) {
                        logger.log(Level.WARNING, "Couldn't register subscription at CalDAV/CardDAV server", e)
                    }
            }
    }

    private suspend fun unsubscribeNotSyncable(service: Service) {
        val unsubscribeFrom = collectionRepository.getPushRegisteredAndNotSyncable(service.id)
        if (unsubscribeFrom.isEmpty())
            return

        val account = accountRepository.get().fromName(service.accountName)
        httpClientBuilder.get()
            .fromAccount(account)
            .build()
            .use { httpClient ->
                for (collection in unsubscribeFrom)
                    collection.pushSubscription?.toHttpUrlOrNull()?.let { url ->
                        logger.info("Unregistering push for ${collection.url}")
                        unsubscribe(httpClient, collection, url)
                    }
            }
    }

    /**
     * Called when no subscription is available (anymore) for the given service.
     *
     * Unsubscribes from all collections.
     */
    internal suspend fun removeSubscription(serviceId: Long) {
        val service = serviceRepository.get(serviceId) ?: return
        val unsubscribeFrom = collectionRepository.getPushRegistered(service.id)
        if (unsubscribeFrom.isEmpty())
            return

        val account = accountRepository.get().fromName(service.accountName)
        httpClientBuilder.get()
            .fromAccount(account)
            .build()
            .use { httpClient ->
                for (collection in unsubscribeFrom)
                    collection.pushSubscription?.toHttpUrlOrNull()?.let { url ->
                        logger.info("Unregistering push for ${collection.url}")
                        unsubscribe(httpClient, collection, url)
                    }
            }
    }


    /**
     * Registers the subscription to a given collection ("subscribe to a collection").
     *
     * @param httpClient    HTTP client to use
     * @param collection    collection to subscribe to
     * @param endpoint      subscription to register
     */
    private suspend fun subscribe(httpClient: HttpClient, collection: Collection, endpoint: PushEndpoint) {
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

        runInterruptible {
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
    }

    private suspend fun unsubscribe(httpClient: HttpClient, collection: Collection, url: HttpUrl) {
        runInterruptible {
            try {
                DavResource(httpClient.okHttpClient, url).delete {
                    // deleted
                }
            } catch (e: DavException) {
                logger.log(Level.WARNING, "Couldn't unregister push for ${collection.url}", e)
            }

            // remove registration URL from DB in any case
            collectionRepository.updatePushSubscription(
                id = collection.id,
                subscriptionUrl = null,
                expires = null
            )
        }
    }


    /**
     * Determines whether there are any push-capable collections and updates the periodic worker accordingly.
     *
     * If there are push-capable collections, a unique periodic worker with an initial delay of 5 seconds is enqueued.
     * A potentially existing worker is replaced, so that the first run should be soon.
     *
     * Otherwise, a potentially existing worker is cancelled.
     */
    fun updatePeriodicWorker() {
        val workerNeeded = runBlocking {
            collectionRepository.anyPushCapable()
        }

        val workManager = WorkManager.getInstance(context)
        if (workerNeeded) {
            logger.info("Enqueuing periodic PushRegistrationWorker")
            workManager.enqueueUniquePeriodicWork(
                WORKER_UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(PushRegistrationWorker::class, WORKER_INTERVAL_DAYS, TimeUnit.DAYS)
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build()
            )
        } else {
            logger.info("Cancelling periodic PushRegistrationWorker")
            workManager.cancelUniqueWork(WORKER_UNIQUE_NAME)
        }
    }


    companion object {

        private const val WORKER_UNIQUE_NAME = "push-registration"
        const val WORKER_INTERVAL_DAYS = 1L

        val dispatcher = Dispatchers.IO.limitedParallelism(1)

    }

}