/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.accounts.Account
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import at.bitfire.dav4jvm.DavCollection
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.property.push.NS_WEBDAV_PUSH
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.repository.PreferenceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Worker that registers push for all collections that support it.
 * To be run as soon as a collection that supports push is changed (selected for sync status
 * changes, or collection is created, deleted, etc).
 */
@Suppress("unused")
@HiltWorker
class PushRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val collectionRepository: DavCollectionRepository,
    private val httpClientBuilder: HttpClient.Builder,
    private val logger: Logger,
    private val preferenceRepository: PreferenceRepository,
    private val serviceRepository: DavServiceRepository
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        logger.info("Running push registration worker")

        try {
            registerSyncable()
            unregisterNotSyncable()
        } catch (_: IOException) {
            return Result.retry()       // retry on I/O errors
        }

        return Result.success()
    }

    private suspend fun registerPushSubscription(collection: Collection, account: Account, endpoint: String) {
        val httpClient = httpClientBuilder
            .fromAccount(account)
            .inForeground(true)
            .build()
        runInterruptible {
            httpClient.use { client ->
                val httpClient = client.okHttpClient

                // requested expiration time: 3 days
                val requestedExpiration = Instant.now() + Duration.ofDays(3)

                val serializer = XmlUtils.newSerializer()
                val writer = StringWriter()
                serializer.setOutput(writer)
                serializer.startDocument("UTF-8", true)
                serializer.insertTag(Property.Name(NS_WEBDAV_PUSH, "push-register")) {
                    serializer.insertTag(Property.Name(NS_WEBDAV_PUSH, "subscription")) {
                        // subscription URL
                        serializer.insertTag(Property.Name(NS_WEBDAV_PUSH, "web-push-subscription")) {
                            serializer.insertTag(Property.Name(NS_WEBDAV_PUSH, "push-resource")) {
                                text(endpoint)
                            }
                        }
                    }
                    // requested expiration
                    serializer.insertTag(Property.Name(NS_WEBDAV_PUSH, "expires")) {
                        text(HttpUtils.formatDate(requestedExpiration))
                    }
                }
                serializer.endDocument()

                val xml = writer.toString().toRequestBody(DavResource.MIME_XML)
                DavCollection(httpClient, collection.url).post(xml) { response ->
                    if (response.isSuccessful) {
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
    }

    private suspend fun registerSyncable() {
        val endpoint = preferenceRepository.unifiedPushEndpoint()

        // register push subscription for syncable collections
        if (endpoint != null)
            for (collection in collectionRepository.getPushCapableAndSyncable()) {
                val expires = collection.pushSubscriptionExpires
                // calculate next run time, but use the duplicate interval for safety (times are not exact)
                val nextRun = Instant.now() + Duration.ofDays(2*PushRegistrationWorkerManager.INTERVAL_DAYS)
                if (expires != null && expires >= nextRun.epochSecond) {
                    logger.fine("Push subscription for ${collection.url} is still valid until ${collection.pushSubscriptionExpires}")
                    continue
                }

                // no existing subscription or expiring soon
                logger.info("Registering push for ${collection.url}")
                serviceRepository.get(collection.serviceId)?.let { service ->
                    val account = Account(service.accountName, applicationContext.getString(R.string.account_type))
                    try {
                        registerPushSubscription(collection, account, endpoint)
                    } catch (e: DavException) {
                        // catch possible per-collection exception so that all collections can be processed
                        logger.log(Level.WARNING, "Couldn't register push for ${collection.url}", e)
                    }
                }
            }
        else
            logger.info("No UnifiedPush endpoint configured")
    }

    private suspend fun unregisterPushSubscription(collection: Collection, account: Account, url: HttpUrl) {
        val httpClient = httpClientBuilder
            .fromAccount(account)
            .inForeground(true)
            .build()
        runInterruptible {
            httpClient.use { client ->
                val httpClient = client.okHttpClient

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
                    expires = null
                )
            }
        }
    }

    private suspend fun unregisterNotSyncable() {
        for (collection in collectionRepository.getPushRegisteredAndNotSyncable()) {
            logger.info("Unregistering push for ${collection.url}")
            collection.pushSubscription?.toHttpUrlOrNull()?.let { url ->
                serviceRepository.get(collection.serviceId)?.let { service ->
                    val account = Account(service.accountName, applicationContext.getString(R.string.account_type))
                    unregisterPushSubscription(collection, account, url)
                }
            }
        }
    }

}