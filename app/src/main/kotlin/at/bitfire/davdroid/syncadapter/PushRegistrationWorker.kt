package at.bitfire.davdroid.syncadapter

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
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.property.push.NS_WEBDAV_PUSH
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.AccountSettings
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Worker that registers push for all collections that support it.
 * To be run as soon as a collection that supports push is changed (selected for sync status
 * changes, or collection is created, deleted, etc).
 *
 * TODO Should run periodically, too. Not required for a first demonstration version.
 */
@Suppress("unused")
@HiltWorker
class PushRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val collectionRepository: DavCollectionRepository,
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
            Logger.log.info("Enqueueing push registration worker")
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)
        }

    }


    private suspend fun requestPushRegistration(collection: Collection, account: Account, endpoint: String) {
        val settings = AccountSettings(applicationContext, account)

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
                        if (response.isSuccessful) runBlocking {
                            response.header("Location")?.let  { subscriptionUrl ->
                                collectionRepository.updatePushSubscription(collection.id, subscriptionUrl)
                            }
                        } else
                            Logger.log.warning("Couldn't register push for ${collection.url}: $response")
                    }
                }
        }
    }

    override suspend fun doWork(): Result {
        Logger.log.info("Running push registration worker")

        // We will get this endpoint from UnifiedPush:
        val sampleEndpoint = "https://endpoint.example.com"

        for (collection in collectionRepository.getSyncEnabledAndPushCapable()) {
            Logger.log.info("Registering push for ${collection.url}")
            val service = serviceRepository.get(collection.serviceId) ?: continue
            val account = Account(service.accountName, applicationContext.getString(R.string.account_type))

            requestPushRegistration(collection, account, sampleEndpoint)
        }

        return Result.success()
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