/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.content.Context
import androidx.work.WorkManager
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Between DAVx5 4.4.1-beta.1 and 4.4.1-rc.1 (both v15), the periodic sync workers were renamed (moved to another
 * package) and thus automatic synchronization stopped (because the enqueued workers rely on the full class
 * name and no new workers were enqueued). Here we enqueue all periodic sync workers again with the correct class name.
 */
class AccountSettingsMigration16 @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val syncWorkerManager: SyncWorkerManager
): AccountSettingsMigration {

    override fun migrate(account: Account, accountSettings: AccountSettings) {
        for (authority in syncWorkerManager.syncAuthorities()) {
            logger.info("Re-enqueuing periodic sync workers for $account/$authority, if necessary")

            /* A maybe existing periodic worker references the old class name (even if it failed and/or is not active). So
            we need to explicitly disable and prune all workers. Just updating the worker is not enough – WorkManager will update
            the work details, but not the class name. */
            val disableOp = syncWorkerManager.disablePeriodic(account, authority)
            disableOp.result.get()  // block until worker with old name is disabled

            val pruneOp = WorkManager.getInstance(context).pruneWork()
            pruneOp.result.get()    // block until worker with old name is removed from DB

            val interval = accountSettings.getSyncInterval(authority)
            if (interval != null && interval != AccountSettings.SYNC_INTERVAL_MANUALLY) {
                // There's a sync interval for this account/authority; a periodic sync worker should be there, too.
                val onlyWifi = accountSettings.getSyncWifiOnly()
                syncWorkerManager.enablePeriodic(account, authority, interval, onlyWifi)
            }
        }
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(16)
        abstract fun provide(impl: AccountSettingsMigration16): AccountSettingsMigration
    }

}