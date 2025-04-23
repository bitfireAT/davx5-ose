/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DelayedSyncManager @Inject constructor(
    private val syncWorkerManager: SyncWorkerManager
) {

    private val delayJobs: ConcurrentHashMap<Account, Job> = ConcurrentHashMap()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Enqueues a one-time sync for given account, after a delay of [DELAY] ms. Resets delay when
     * called again before delay finishes.
     * @param account   account to sync
     */
    fun enqueueAfterDelay(account: Account) {
        // Atomically cancel, launch and remember delay coroutine of given account
        delayJobs.compute(account) { _, previousJob ->
            // Stop previous delay, if exists
            previousJob?.cancel()

            // Start delay and enqueue sync on finish
            applicationScope.launch {
                delay(DELAY)
                syncWorkerManager.enqueueOneTimeAllAuthorities(account)
            }
        }
    }

    companion object {

        /**
         * Length of delay in milliseconds
         */
        const val DELAY = 10000L // 10 seconds

    }

}