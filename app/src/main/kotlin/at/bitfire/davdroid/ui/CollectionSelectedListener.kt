/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import at.bitfire.davdroid.push.PushRegistrationManager
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.davdroid.ui.CollectionSelectedListener.Companion.DELAY_MS
import at.bitfire.davdroid.util.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionSelectedListener @Inject constructor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val pushRegistrationManager: PushRegistrationManager,
    private val syncWorkerManager: SyncWorkerManager
) {

    private val delayJobs: ConcurrentHashMap<Account, Job> = ConcurrentHashMap()
    private val scope = CoroutineScope(SupervisorJob())

    /**
     * After a delay of [DELAY_MS] ms:
     *
     * 1. Enqueues a one-time sync for given account.
     * 2. Updates push subscriptions for given service (if any).
     *
     * Resets delay when called again before delay finishes.
     *
     * @param account       account to sync
     * @param serviceId     DB service to update push subscriptions for
     */
    fun enqueueAfterDelay(account: Account, serviceId: Long? = null) {
        // Atomically cancel, launch and remember delay coroutine of given account
        delayJobs.compute(account) { _, previousJob ->
            // Stop previous delay, if exists
            previousJob?.cancel()

            scope.launch(defaultDispatcher) {
                // wait
                delay(DELAY_MS)

                // enqueue sync
                syncWorkerManager.enqueueOneTimeAllAuthorities(account)

                // update push subscriptions
                if (serviceId != null)
                    pushRegistrationManager.update(serviceId)

                // remove complete job
                delayJobs -= account
            }
        }
    }


    companion object {

        /**
         * Length of delay in milliseconds
         */
        const val DELAY_MS = 5000L     // 5 seconds

    }

}