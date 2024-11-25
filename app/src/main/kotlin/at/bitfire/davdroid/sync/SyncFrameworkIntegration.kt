package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentResolver
import android.provider.CalendarContract
import androidx.annotation.WorkerThread
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Handles all Sync Adapter Framework related interaction. Other classes should never call
 * `ContentResolver.setIsSyncable()` or something similar themselves. Everything sync-framework
 * related must be handled by this class.
 *
 * Sync requests from the Sync Adapter Framework are handled by [SyncAdapterService].
 */
class SyncFrameworkIntegration @Inject constructor(
    private val logger: Logger
) {

    /**
     * Gets the global auto-sync setting that applies to all the providers and accounts. If this is
     * false then the per-provider auto-sync setting is ignored.
     */
    fun getMasterSyncAutomatically() =
        ContentResolver.getMasterSyncAutomatically()

    /**
     * Check if this account/provider is syncable.
     */
    fun isSyncable(account: Account, authority: String): Boolean =
        ContentResolver.getIsSyncable(account, authority) > 0

    /**
     * Enable this account/provider to be syncable.
     */
    fun enableSyncAbility(account: Account, authority: String) {
        if (ContentResolver.getIsSyncable(account, authority) != 1)
            ContentResolver.setIsSyncable(account, authority, 1)
    }

    /**
     * Disable this account/provider to be syncable.
     */
    fun disableSyncAbility(account: Account, authority: String) {
        if (ContentResolver.getIsSyncable(account, authority) != 0)
            ContentResolver.setIsSyncable(account, authority, 0)
    }

    /**
     * Check if the provider should be synced when content (contact, calendar event or task) changes.
     */
    fun syncsOnContentChange(account: Account, authority: String) =
        ContentResolver.getSyncAutomatically(account, authority)

    /**
     * Enable syncing on content (contact, calendar event or task) changes.
     */
    fun enableSyncOnContentChange(account: Account, authority: String) {
        if (!ContentResolver.getSyncAutomatically(account, authority))
            setSyncOnContentChange(account, authority, true)
    }

    /**
     * Disable syncing on content (contact, calendar event or task) changes.
     */
    fun disableSyncOnContentChange(account: Account, authority: String) {
        if (ContentResolver.getSyncAutomatically(account, authority))
            setSyncOnContentChange(account, authority, false)
    }

    /**
     * Enables/disables sync adapter automatic sync (content triggered sync) for the given
     * account and authority. Does *not* call [ContentResolver.setIsSyncable].
     *
     * We use the sync adapter framework only for the trigger, actual syncing is implemented
     * with WorkManager. The trigger comes in through SyncAdapterService.
     *
     * Because there is no callback for when the sync status/interval has been updated, this method
     * blocks until the sync-on-content-change has been enabled or disabled, so it should not be
     * called from the UI thread.
     *
     * @param account   account to enable/disable content change sync triggers for
     * @param enable    *true* enables automatic sync; *false* disables it
     * @param authority sync authority (like [CalendarContract.AUTHORITY])
     * @return whether the content triggered sync was enabled successfully
     */
    @WorkerThread
    private fun setSyncOnContentChange(account: Account, authority: String, enable: Boolean): Boolean {
        // Try up to 10 times with 100 ms pause
        repeat(10) {
            if (setContentTrigger(account, authority, enable)) {
                // Remove periodic syncs created by ContentResolver.setSyncAutomatically
                ContentResolver.getPeriodicSyncs(account, authority).forEach { periodicSync ->
                    ContentResolver.removePeriodicSync(
                        periodicSync.account,
                        periodicSync.authority,
                        periodicSync.extras
                    )
                }
                // Set successfully
                return true
            }
            Thread.sleep(100)
        }
        // Failed to set
        return false
    }

    /**
     * Enable or disable content change sync triggers of the Sync Adapter Framework.
     *
     * @param account   account to enable/disable content change sync triggers for
     * @param enable    *true* enables automatic sync; *false* disables it
     * @param authority sync authority (like [CalendarContract.AUTHORITY])
     * @return whether the content triggered sync was enabled successfully
     */
    private fun setContentTrigger(account: Account, authority: String, enable: Boolean): Boolean =
        if (enable) {
            logger.fine("Enabling content-triggered sync of $account/$authority")
            ContentResolver.setSyncAutomatically(account, authority, true)
            /* return */ ContentResolver.getSyncAutomatically(account, authority)
        } else {
            logger.fine("Disabling content-triggered sync of $account/$authority")
            ContentResolver.setSyncAutomatically(account, authority, false)
            /* return */ !ContentResolver.getSyncAutomatically(account, authority)
        }

}