/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import at.bitfire.davdroid.push.PushRegistrationManager
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.davdroid.ui.CollectionSelectedUseCase.Companion.DELAY_MS
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

/**
 * Performs actions when a collection was (un)selected for synchronization.
 *
 * @see handleWithDelay
 */
@Singleton
class CollectionSelectedUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val collectionRepository: DavCollectionRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val pushRegistrationManager: PushRegistrationManager,
    private val serviceRepository: DavServiceRepository,
    private val syncWorkerManager: SyncWorkerManager
) {

    private val delayJobs: ConcurrentHashMap<Account, Job> = ConcurrentHashMap()
    private val scope = CoroutineScope(SupervisorJob())

    /**
     * After a delay of [DELAY_MS] ms:
     *
     * 1. Enqueues a one-time sync for account of the collection.
     * 2. Updates push subscriptions for the service of the collection.
     *
     * Resets delay when called again before delay finishes.
     *
     * @param collectionId  ID of the collection that was (un)selected for synchronization
     */
    suspend fun handleWithDelay(collectionId: Long) {
        val collection = collectionRepository.getAsync(collectionId) ?: return
        val service = serviceRepository.getAsync(collection.id) ?: return
        val account = accountRepository.fromName(service.accountName)

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
                pushRegistrationManager.update(service.id)

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