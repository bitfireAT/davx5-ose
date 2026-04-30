/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.HttpUtils.toKtorUrl
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.ktor.DavCollection
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.toUrlOrNull
import at.bitfire.dav4jvm.property.push.WebDAVPush
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.push.PushRegistrationManager.Companion.mutex
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.davdroid.ui.NotificationRegistry
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val distributorManager: PushDistributorManager,
    private val notificationManager: PushNotificationManager,
    private val notificationRegistry: NotificationRegistry,
    private val httpClientBuilder: Provider<HttpClientBuilder>,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository
) {

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

        val isDistributorSelected = distributorManager.getDistributorToUse() != null
        if (isDistributorSelected)
            try {
                val vapid = collectionRepository.getVapidKey(serviceId)
                if (vapid != null) {    // only register when there's a VAPID key
                    logger.fine("Registering UnifiedPush instance for service $instance / ${service.accountName}")

                    // message for distributor
                    val message = "${service.accountName} (${service.type})"

                    UnifiedPush.register(context, instance, message, vapid)
                } else {
                    logger.fine("No VAPID key for service $serviceId / ${service.accountName}")
                    /* We don't call UnifiedPush.unregister(context, instance) here because it can
                    remove the push distributor. May be improved in the future. */
                }
            } catch (e: UnifiedPush.VapidNotValidException) {
                logger.log(Level.WARNING, "Couldn't register invalid VAPID key for service $serviceId", e)
            }
        else if (distributorManager.isPushEnabled()) {
            unregisterAndUnsubscribeFromService(service)
            notifyDistributorSelection()
        }

        // UnifiedPush has now been called. It will do its work and then asynchronously call back to UnifiedPushService, which
        // will then call processSubscription or removeSubscription.
    }

    private suspend fun notifyDistributorSelection() {
        withContext(Dispatchers.Main) {
            notificationManager.notify(
                id = NotificationRegistry.NOTIFY_SELECT_PUSH_DISTRIBUTOR,
                channelId = notificationRegistry.CHANNEL_USER_INTERACTION,
                title = context.getString(R.string.push_distributor_selection_required_title),
                text = context.getString(R.string.push_distributor_selection_required_message),
                priority = NotificationCompat.PRIORITY_DEFAULT,
                category = NotificationCompat.CATEGORY_RECOMMENDATION,
                intent = Intent(context, PushDistributorSelectionActivity::class.java)
            )
        }
    }

    /**
     * Unregisters instances and unsubscribes from the given [service].
     */
    private suspend fun unregisterAndUnsubscribeFromService(service: Service) {
        val instance = service.id.toString()
        UnifiedPush.unregister(context, instance)   // doesn't call UnifiedPushService.onUnregistered
        unsubscribeAll(service)
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

    /**
     * (Re-)subscribes to push notifications for syncable collections of a given service using the provided endpoint.
     *
     * @param service The service for which to subscribe to push notifications. Must not be null.
     * @param endpoint The push endpoint to use for subscription. Must not be null.
     */
    private suspend fun subscribeSyncable(service: Service, endpoint: PushEndpoint) {
        val subscribeTo = collectionRepository.getPushCapableAndSyncable(service.id)
        if (subscribeTo.isEmpty())
            return

        // calculate next worker run (later needed to check expiry); duplicate days for safety (times are not exact)
        val nextWorkerRun = Instant.now() + Duration.ofDays(2 * WORKER_INTERVAL_DAYS)

        val account = accountRepository.get().fromName(service.accountName)
        httpClientBuilder.get()
            .fromAccountAsync(account)
            .buildKtor()
            .use { httpClient ->
            for (collection in subscribeTo) {
                // update push subscription for the given collection
                try {
                    // determine whether the registered subscription will expire before the next worker run ...
                    val subscriptionAboutToExpire = collection.pushSubscriptionExpires?.let { nextWorkerRun.epochSecond >= it } ?: true
                    // ... and also check whether endpoint has changed
                    val endpointChanged = collection.pushRegisteredEndpoint == null || collection.pushRegisteredEndpoint != endpoint.url
                    if (!endpointChanged && !subscriptionAboutToExpire)
                        logger.fine("Push subscription for ${collection.url} is still valid until ${collection.pushSubscriptionExpires}")
                    else {
                        if (endpointChanged) {
                            logger.fine("Push endpoint changed for ${collection.url}, unsubscribing from old endpoint first")
                            collection.pushSubscription?.toUrlOrNull()?.let { oldUrl ->
                                unsubscribe(httpClient, collection, oldUrl)
                            }
                        }
                        logger.fine("Registering push subscription for ${collection.url}")
                        subscribe(httpClient, collection, endpoint)
                    }
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Couldn't register subscription at CalDAV/CardDAV server", e)
                }
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
        serializer.insertTag(WebDAVPush.PushRegister) {
            serializer.insertTag(WebDAVPush.Subscription) {
                // subscription URL
                serializer.insertTag(WebDAVPush.WebPushSubscription) {
                    serializer.insertTag(WebDAVPush.PushResource) {
                        text(endpoint.url)
                    }
                    endpoint.pubKeySet?.let { pubKeySet ->
                        serializer.insertTag(WebDAVPush.SubscriptionPublicKey) {
                            attribute(null, "type", "p256dh")
                            text(pubKeySet.pubKey)
                        }
                        serializer.insertTag(WebDAVPush.AuthSecret) {
                            text(pubKeySet.auth)
                        }
                    }
                }
            }
            // requested expiration
            serializer.insertTag(WebDAVPush.Expires) {
                text(HttpUtils.formatDate(requestedExpiration))
            }
        }
        serializer.endDocument()

        DavCollection(httpClient, collection.url.toKtorUrl()).post(
            TextContent(writer.toString(), DavResource.MIME_XML_UTF8)
        ) { response ->
            if (response.status.isSuccess()) {
                // update subscription URL and expiration in DB
                val subscriptionUrl = response.headers[HttpHeaders.Location]
                val expires = response.headers[HttpHeaders.Expires]?.let { expiresDate ->
                    HttpUtils.parseDate(expiresDate)
                } ?: requestedExpiration

                collectionRepository.updatePushSubscription(
                    id = collection.id,
                    subscriptionUrl = subscriptionUrl,
                    registeredEndpoint = endpoint.url,
                    expires = expires?.epochSecond
                )
            } else
                logger.warning("Couldn't register push for ${collection.url}: $response")
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
            .buildKtor()
            .use { httpClient ->
            for (collection in from)
                collection.pushSubscription?.toUrlOrNull()?.let { url ->
                    logger.info("Unsubscribing Push from ${collection.url}")
                    unsubscribe(httpClient, collection, url)
                }
        }
    }

    private suspend fun unsubscribe(httpClient: HttpClient, collection: Collection, url: Url) {
        try {
            DavResource(httpClient, url).delete {
                // deleted
            }
        } catch (e: DavException) {
            logger.log(Level.WARNING, "Couldn't unregister push for ${collection.url}", e)
        }

        // remove registration URL from DB in any case
        collectionRepository.updatePushSubscription(
            id = collection.id,
            subscriptionUrl = null,
            registeredEndpoint = null,
            expires = null
        )
    }


    /**
     * Determines whether there are any push-capable collections and updates the
     * [PushRegistrationWorker] accordingly.
     *
     * If there are push-capable collections, a unique periodic worker is enqueued.
     *
     * If there are no push-capable collections, a potentially existing worker is canceled.
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