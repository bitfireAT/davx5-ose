package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.property.push.NS_WEBDAV_PUSH
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.DavCollectionRepository
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
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltWorker
class PushRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    val collectionRepository: DavCollectionRepository,
    val db: AppDatabase
) : CoroutineWorker(context, workerParameters) {
    companion object {
        private const val UNIQUE_WORK_NAME = "push-registration"

        fun enqueue(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<PushRegistrationWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
            Logger.log.info("Enqueueing push registration worker")
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }

    // TODO - Move to dav4jvm
    private suspend fun requestPushRegistration(collection: Collection, account: Account) {
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
                                    text("https://endpoint.example.com")
                                }
                            }
                        }
                    }
                    serializer.endDocument()

                    DavResource(httpClient, collection.url).post(
                        writer.toString().toRequestBody(DavResource.MIME_XML)
                    ) { response ->
                        // TODO
                    }
                }
        }
    }

    override suspend fun doWork(): Result {
        Logger.log.info("Running push registration worker")
        val collections = collectionRepository.getSyncEnabledAndPushCapable()

        for (collection in collections) {
            Logger.log.info("Registering push for ${collection.url}")
            val service = db.serviceDao().get(collection.serviceId)!!
            val account = Account(service.accountName, applicationContext.getString(R.string.account_type))

            requestPushRegistration(collection, account)
        }

        return Result.success()
    }

    class DavCollectionRepositoryListener @Inject constructor(
        @ApplicationContext val context: Context
    ): DavCollectionRepository.OnChangeListener {
        override fun onCollectionsChanged() = enqueue(context)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface PushRegistrationWorkerModule {
        @Binds
        @IntoSet
        fun listener(impl: DavCollectionRepositoryListener): DavCollectionRepository.OnChangeListener
    }

}
