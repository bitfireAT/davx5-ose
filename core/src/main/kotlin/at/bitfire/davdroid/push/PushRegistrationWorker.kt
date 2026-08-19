/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import at.bitfire.davdroid.di.qualifier.SyncDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.logging.Logger

/**
 * Worker that updates push subscriptions / registrations for all collections.
 *
 * Managed by [PushRegistrationManager].
 */
@Suppress("unused")
@HiltWorker
class PushRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val logger: Logger,
    private val pushRegistrationManager: PushRegistrationManager,
    @SyncDispatcher private val syncDispatcher: CoroutineDispatcher
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result =
        withContext(syncDispatcher) {   // required because of Ktor issue; see SyncDispatcher KDoc
            updatePushRegistrations()
        }

    suspend fun updatePushRegistrations(): Result {
        logger.info("Running push registration worker")

        // update registrations for all services
        pushRegistrationManager.update()

        return Result.success()
    }

}