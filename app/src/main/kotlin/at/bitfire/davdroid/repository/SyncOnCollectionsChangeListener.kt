/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Enqueues a sync worker after a short delay when the collection list changes.
 */
class SyncOnCollectionsChangeListener @Inject constructor(
    private val workerManager: SyncWorkerManager,
    private val logger: Logger
): DavCollectionRepository.OnChangeListener {

    var delayedOneTimeSyncWorkerJob: Job? = null

    override fun onCollectionsChanged(account: Account?) {
        account?.let {
            // Start sync after a short delay to avoid multiple syncs in a short time when multiple
            // collections change quickly, e.g. at collection refresh or users first time setup.
            delayedOneTimeSyncWorkerJob?.cancel()
            delayedOneTimeSyncWorkerJob = CoroutineScope(Dispatchers.IO).launch {
                delay(7000)
                logger.info("Collections changed, scheduling sync")
                workerManager.enqueueOneTimeAllAuthorities(it)
            }
        }
    }



    /**
     * Hilt module that registers [SyncOnCollectionsChangeListener] in [DavCollectionRepository].
     */
    @Module
    @InstallIn(SingletonComponent::class)
    interface SyncOnCollectionsChangeListenerModule {
        @Binds
        @IntoSet
        fun listener(impl: SyncOnCollectionsChangeListener): DavCollectionRepository.OnChangeListener
    }

}