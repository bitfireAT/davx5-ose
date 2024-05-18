package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.app.Application
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
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.AccountSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runInterruptible
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@HiltWorker
class PushRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
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

                    // TODO - Add proper POST method in dav4jvm
                    DavResource(httpClient, collection.url)
                        .httpClient
                        .newCall(
                            Request.Builder()
                                .post(
                                    writer.toString().toRequestBody(DavResource.MIME_XML)
                                )
                                .url(collection.url)
                                .build()
                        )
                        .execute()
                }
        }
    }

    override suspend fun doWork(): Result {
        Logger.log.info("Running push registration worker")
        val repo = DavCollectionRepository(applicationContext as Application, db)
        val collections = repo.getSyncEnabledAndPushCapable()

        for (collection in collections) {
            Logger.log.info("Registering push for ${collection.url}")
            val service = db.serviceDao().get(collection.serviceId)!!
            val account = Account(service.accountName, applicationContext.getString(R.string.account_type))

            requestPushRegistration(collection, account)
        }

        return Result.success()
    }
}
