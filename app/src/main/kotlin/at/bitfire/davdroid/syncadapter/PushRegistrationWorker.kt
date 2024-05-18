package at.bitfire.davdroid.syncadapter

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.repository.DavCollectionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

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

    override suspend fun doWork(): Result {
        Logger.log.info("Running push registration worker")
        val repo = DavCollectionRepository(applicationContext as Application, db)
        val collections = repo.getSyncEnabledAndPushCapable()

        for (collection in collections) {
            Logger.log.info("Registering push for ${collection.url}")
        }

        return Result.success()
    }
}
