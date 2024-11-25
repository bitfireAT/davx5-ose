package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentResolver
import android.provider.CalendarContract
import androidx.annotation.WorkerThread
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles all Sync Adapter Framework related interaction. Other classes should never call
 * `ContentResolver.setIsSyncable()` or something similar themselves. Everything sync-framework
 * related must be handled by this class.
 *
 * Sync requests from the sync adapter framework are handled by [SyncAdapterService].
 */
@Singleton
class SyncFrameworkIntegration @Inject constructor(
    private val logger: Logger
) {

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
     * This method blocks until the sync-on-content-change has been enabled or disabled, so it
     * should not be called from the UI thread.
     *
     * @param enable    *true* enables automatic sync; *false* disables it
     * @param authority sync authority (like [CalendarContract.AUTHORITY])
     * @return whether the content triggered sync was enabled successfully
     */
    @WorkerThread
    private fun setSyncOnContentChange(account: Account, authority: String, enable: Boolean): Boolean {
        // Enable content change triggers (sync adapter framework)
        val setContentTrigger: () -> Boolean =
            /* Ugly hack: because there is no callback for when the sync status/interval has been
            updated, we need to make this call blocking. */
            if (enable) {{
                logger.fine("Enabling content-triggered sync of $account/$authority")
                ContentResolver.setSyncAutomatically(account, authority, true) // enables content triggers
                /* return */ ContentResolver.getSyncAutomatically(account, authority)
            }} else {{
                logger.fine("Disabling content-triggered sync of $account/$authority")
                ContentResolver.setSyncAutomatically(account, authority, false) // disables content triggers
                /* return */ !ContentResolver.getSyncAutomatically(account, authority)
            }}

        // try up to 10 times with 100 ms pause
        repeat(10) {
            if (setContentTrigger()) {
                // Successfully set
                // Remove periodic syncs created by ContentResolver.setSyncAutomatically
                ContentResolver.getPeriodicSyncs(account, authority).forEach { periodicSync ->
                    ContentResolver.removePeriodicSync(
                        periodicSync.account,
                        periodicSync.authority,
                        periodicSync.extras
                    )
                }
                return true
            }
            Thread.sleep(100)
        }
        return false
    }

}