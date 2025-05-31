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
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.push.PushRegistrationManager.Companion.mutex
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.sync.account.InvalidAccountException
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/**
 * Manages push registrations and subscriptions.
 *
 * To update push registrations and subscriptions (for instance after collections have been changed), call [update].
 *
 * Public API calls are protected by [mutex] so that there won't be multiple subscribe/unsubscribe operations at the same time.
 * If you call other methods than [update], make sure that they don't interfere with other operations.
 */
class PushRegistrationManager @Inject constructor(
    private val accountRepository: Lazy<AccountRepository>,
    private val collectionRepository: DavCollectionRepository,
    @ApplicationContext private val context: Context,
    private val httpClientBuilder: Provider<HttpClient.Builder>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository
) {

    /**
     * Sets or removes (disable push) the distributor and updates the subscriptions + worker.
     *
     * Uses [update] which is protected by [mutex] so creating/deleting subscriptions doesn't
     * interfere with other operations.
     *
     * @param pushDistributor  new distributor or `null` to disable Push
     */
    suspend fun setPushDistributor(pushDistributor: String?) {
        // Disable UnifiedPush and remove all subscriptions
        UnifiedPush.removeDistributor(context)
        update()

        if (pushDistributor != null) {
            // If a distributor was passed, store it and create/register subscriptions
            UnifiedPush.saveDistributor(context, pushDistributor)
            update()
        }
    }

    fun getCurrentDistributor() = UnifiedPush.getSavedDistributor(context)

    fun getDistributors() = UnifiedPush.getDistributors(context)


    /**
     * Updates all push registrations and subscriptions so that if Push is available, it's up-to-date and
     * working for all database services. If Push is not available, existing subscriptions are unregistered.
     *
     * Also makes sure that the [PushRegistrationWorker] is enabled if there's a Push-enabled collection.
     *
     * Acquires [mutex] so that this method can't be called twice at the same time, or at the same time
     * with [update(serviceId)].
     */
    suspend fun update() = mutex.withLock {
        for (service in serviceRepository.getAll())
            updateService(service.id)

        updatePeriodicWorker()
    }

    /**
     * Same as [update], but for a specific database service.
     *
     * Acquires [mutex] so that this method can't be called twice at the same time, or at the same time
     * as [update()].
     */
    suspend fun update(serviceId: Long) = mutex.withLock {
        updateService(serviceId)
        updatePeriodicWorker()
    }

    /**
     * Registers or unregisters subscriptions depending on whether there is a distributor available.
     */
    private suspend fun updateService(serviceId: Long) {
        val service = serviceRepository.get(serviceId) ?: return

        // use service ID from database as UnifiedPush instance name
        val instance = serviceId.toString()

        val distributorAvailable = getCurrentDistributor() != null
        if (distributorAvailable)
            try {
                val vapid = collectionRepository.getVapidKey(serviceId)
                logger.fine("Registering UnifiedPush instance $serviceId (${service.accountName})")

                // message for distributor
                val message = "${service.accountName} (${service.type})"

                UnifiedPush.register(context, instance, message, vapid)
            } catch (e: UnifiedPush.VapidNotValidException) {
                logger.log(Level.WARNING, "Couldn't register invalid VAPID key for service $serviceId", e)
            }
        else {
            logger.fine("Unregistering UnifiedPush instance $serviceId (${service.accountName})")
            UnifiedPush.unregister(context, instance)   // doesn't call UnifiedPushService.onUnregistered
            unsubscribeAll(service)
        }

        // UnifiedPush has now been called. It will do its work and then asynchronously call back to UnifiedPushService, which
        // will then call processSubscription or removeSubscription.
    }

    /**
     * Called by [UnifiedPushService] when a subscription (endpoint) is available for the given service.
     *
     * Uses the subscription to subscribe to syncable collections, and then unsubscribes from non-syncable collections.
     */
    suspend fun processSubscription(serviceId: Long, endpoint: PushEndpoint) = mutex.withLock {
        val service = serviceRepository.get(serviceId) ?: return

        try {
            // subscribe to collections which are selected for synchronization
            subscribeSyncable(service, endpoint)

            // unsubscribe from collections which are not selected for synchronization
            unsubscribeCollections(service, collectionRepository.getPushRegisteredAndNotSyncable(service.id))
        } catch (_: InvalidAccountException) {
            // couldn't create authenticating HTTP client because account is not available
        }
    }

    private suspend fun subscribeSyncable(service: Service, endpoint: PushEndpoint) {
        val subscribeTo = collectionRepository.getPushCapableAndSyncable(service.id)
        if (subscribeTo.isEmpty())
            return

        val account = accountRepository.get().fromName(service.accountName)
        httpClientBuilder.get()
            .fromAccountAsync(account)
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

    /**
     * Called when no subscription is available (anymore) for the given service.
     *
     * Unsubscribes from all subscribed collections.
     */
    suspend fun removeSubscription(serviceId: Long) = mutex.withLock {
        val service = serviceRepository.get(serviceId) ?: return
        unsubscribeAll(service)
    }

    private suspend fun unsubscribeAll(service: Service) {
        val unsubscribeFrom = collectionRepository.getPushRegistered(service.id)

        try {
            unsubscribeCollections(service, unsubscribeFrom)
        } catch (_: InvalidAccountException) {
            // couldn't create authenticating HTTP client because account is not available
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

        runInterruptible(ioDispatcher) {
            val xml = writer.toString().toRequestBody(DavResource.MIME_XML)
            DavCollection(httpClient.okHttpClient, collection.url).post(xml) { response ->
                if (response.isSuccessful) {
                    // update subscription URL and expiration in DB
                    val subscriptionUrl = response.header("Location")
                    val expires = response.header("Expires")?.let { expiresDate ->
                        HttpUtils.parseDate(expiresDate)
                    } ?: requestedExpiration

                    runBlocking {
                        collectionRepository.updatePushSubscription(
                            id = collection.id,
                            subscriptionUrl = subscriptionUrl,
                            expires = expires?.epochSecond
                        )
                    }
                } else
                    logger.warning("Couldn't register push for ${collection.url}: $response")
            }
        }
    }

    /**
     * Unsubscribe from the given collections.
     */
    private suspend fun unsubscribeCollections(service: Service, from: List<Collection>) {
        if (from.isEmpty())
            return

        val account = accountRepository.get().fromName(service.accountName)
        httpClientBuilder.get()
            .fromAccountAsync(account)
            .build()
            .use { httpClient ->
                for (collection in from)
                    collection.pushSubscription?.toHttpUrlOrNull()?.let { url ->
                        logger.info("Unsubscribing Push from ${collection.url}")
                        unsubscribe(httpClient, collection, url)
                    }
            }
    }

    private suspend fun unsubscribe(httpClient: HttpClient, collection: Collection, url: HttpUrl) {
        try {
            runInterruptible(ioDispatcher) {
                DavResource(httpClient.okHttpClient, url).delete {
                    // deleted
                }
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


    /**
     * Determines whether there are any push-capable collections and updates the periodic worker accordingly.
     *
     * If there are push-capable collections, a unique periodic worker with an initial delay of 5 seconds is enqueued.
     * A potentially existing worker is replaced, so that the first run should be soon.
     *
     * Otherwise, a potentially existing worker is cancelled.
     */
    private suspend fun updatePeriodicWorker() {
        val workerNeeded = collectionRepository.anyPushCapable()

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

        /**
         * Mutex to synchronize (un)subscription.
         */
        val mutex = Mutex()

    }

}