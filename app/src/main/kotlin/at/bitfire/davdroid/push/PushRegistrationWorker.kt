/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import at.bitfire.davdroid.repository.DavServiceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.logging.Logger

/**
 * Worker that runs regularly and initiates push registration updates for all collections.
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
    private val serviceRepository: DavServiceRepository
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        logger.info("Running push registration worker")

        // update registrations for all services
        pushRegistrationManager.update()

        return Result.success()
    }

}