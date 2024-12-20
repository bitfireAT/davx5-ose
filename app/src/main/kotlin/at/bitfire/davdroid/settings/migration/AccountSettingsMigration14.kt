/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.TaskProvider
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
 * Disables all sync adapter periodic syncs for every authority. Then enables
 * corresponding PeriodicSyncWorkers
 */
class AccountSettingsMigration14 @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
): AccountSettingsMigration {

    override fun migrate(account: Account, accountSettings: AccountSettings) {
        // Cancel any potentially running syncs for this account (sync framework)
        ContentResolver.cancelSync(account, null)

        val authorities = listOf(
            context.getString(R.string.address_books_authority),
            CalendarContract.AUTHORITY,
            TaskProvider.ProviderName.JtxBoard.authority,
            TaskProvider.ProviderName.OpenTasks.authority,
            TaskProvider.ProviderName.TasksOrg.authority
        )

        for (authority in authorities) {
            // Enable PeriodicSyncWorker (WorkManager), with known intervals
            enableWorkManager(account, authority, accountSettings)
            // Disable periodic syncs (sync adapter framework)
            disableSyncFramework(account, authority)
        }
    }

    private fun enableWorkManager(account: Account, authority: String, accountSettings: AccountSettings) {
        val enabled = accountSettings.getSyncInterval(authority)?.let { syncInterval ->
            accountSettings.setSyncInterval(authority, syncInterval)
        } ?: false
        logger.info("PeriodicSyncWorker for $account/$authority enabled=$enabled")
    }

    private fun disableSyncFramework(account: Account, authority: String) {
        // Disable periodic syncs (sync adapter framework)
        val disable: () -> Boolean = {
            /* Ugly hack: because there is no callback for when the sync status/interval has been
            updated, we need to make this call blocking. */
            for (sync in ContentResolver.getPeriodicSyncs(account, authority))
                ContentResolver.removePeriodicSync(sync.account, sync.authority, sync.extras)

            // check whether syncs are really disabled
            var result = true
            for (sync in ContentResolver.getPeriodicSyncs(account, authority)) {
                logger.info("Sync framework still has a periodic sync for $account/$authority: $sync")
                result = false
            }
            result
        }
        // try up to 10 times with 100 ms pause
        var success = false
        for (idxTry in 0 until 10) {
            success = disable()
            if (success)
                break
            Thread.sleep(200)
        }
        logger.info("Sync framework periodic syncs for $account/$authority disabled=$success")
    }


    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(14)
        abstract fun provide(impl: AccountSettingsMigration14): AccountSettingsMigration
    }

}