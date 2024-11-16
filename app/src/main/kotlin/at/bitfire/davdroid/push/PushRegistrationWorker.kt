/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.accounts.Account
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
import at.bitfire.davdroid.settings.AccountSettings
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Worker that registers push for all collections that support it.
 * To be run as soon as a collection that supports push is changed (selected for sync status
 * changes, or collection is created, deleted, etc).
 *
 * TODO Should run periodically, too (to refresh registrations that are about to expire).
 * Not required for a first demonstration version.
 */
@Suppress("unused")
@HiltWorker
class PushRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val collectionRepository: DavCollectionRepository,
    private val logger: Logger,
    private val preferenceRepository: PreferenceRepository,
    private val serviceRepository: DavServiceRepository
) : CoroutineWorker(context, workerParameters) {

    companion object {

        private const val UNIQUE_WORK_NAME = "push-registration"

        /**
         * Enqueues a push registration worker with a minimum delay of 5 seconds.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)   // require a network connection
                .build()
            val workRequest = OneTimeWorkRequestBuilder<PushRegistrationWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .build()
            Logger.getGlobal().info("Enqueueing push registration worker")
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)
        }

    }


    override suspend fun doWork(): Result {
        logger.info("Running push registration worker")

        registerSyncable()
        unregisterNotSyncable()

        return Result.success()
    }

    private suspend fun registerPushSubscription(collection: Collection, account: Account, endpoint: String) {
        val settings = accountSettingsFactory.create(account)

        runInterruptible {
            HttpClient.Builder(applicationContext, settings)
                .setForeground(true)
                .build()
                .use { client ->
                    val httpClient = client.okHttpClient

                    val serializer = XmlUtils.newSerializer()
                    val writer = StringWriter()
                    serializer.setOutput(writer)
                    serializer.startDocument("UTF-8", true)
                    serializer.insertTag(Property.Name(NS_WEBDAV_PUSH, "push-register")) {
                        serializer.insertTag(Property.Name(NS_WEBDAV_PUSH, "subscription")) {
                            serializer.insertTag(Property.Name(NS_WEBDAV_PUSH, "web-push-subscription")) {
                                serializer.insertTag(Property.Name(NS_WEBDAV_PUSH, "push-resource")) {
                                    text(endpoint)
                                }
                            }
                        }
                    }
                    serializer.endDocument()

                    val xml = writer.toString().toRequestBody(DavResource.MIME_XML)
                    DavCollection(httpClient, collection.url).post(xml) { response ->
                        if (response.isSuccessful) {
                            // store subscription URL and expiration
                            val subscriptionUrl = response.header("Location")
                            val expires = response.header("Expires")?.let { expiresDate ->
                                HttpUtils.parseDate(expiresDate)
                            } ?: (Instant.now() + Duration.ofDays(3))
                            collectionRepository.updatePushSubscription(collection.id, subscriptionUrl, expires?.epochSecond)
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
        val settings = accountSettingsFactory.create(account)

        runInterruptible {
            HttpClient.Builder(applicationContext, settings)
                .setForeground(true)
                .build()
                .use { client ->
                    val httpClient = client.okHttpClient

                    try {
                        DavResource(httpClient, url).delete {
                            // deleted
                        }
                    } catch (e: DavException) {
                        logger.log(Level.WARNING, "Couldn't unregister push for ${collection.url}", e)
                    }

                    // remove registration URL from DB in any case
                    collectionRepository.updatePushSubscription(collection.id, null, null)
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


    /**
     * Listener that enqueues a push registration worker when the collection list changes.
     */
    class CollectionsListener @Inject constructor(
        @ApplicationContext val context: Context
    ): DavCollectionRepository.OnChangeListener {
        override fun onCollectionsChanged() = enqueue(context)
    }

    /**
     * Hilt module that registers [CollectionsListener] in [DavCollectionRepository].
     */
    @Module
    @InstallIn(SingletonComponent::class)
    interface PushRegistrationWorkerModule {
        @Binds
        @IntoSet
        fun listener(impl: CollectionsListener): DavCollectionRepository.OnChangeListener
    }

}