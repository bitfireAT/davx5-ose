/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import at.bitfire.davdroid.repository.DavCollectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import javax.inject.Inject

class PushRegistrationWorkerManager @Inject constructor(
    @ApplicationContext val context: Context,
    val collectionRepository: DavCollectionRepository,
    val logger: Logger
) {

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
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                PeriodicWorkRequest.Builder(PushRegistrationWorker::class, 1, TimeUnit.DAYS)
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
            )
        } else {
            logger.info("Cancelling periodic PushRegistrationWorker")
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }


    companion object {
        private const val UNIQUE_WORK_NAME = "push-registration"
    }

}