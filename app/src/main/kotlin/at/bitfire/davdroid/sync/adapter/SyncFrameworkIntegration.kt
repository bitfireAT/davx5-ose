/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.adapter

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.SyncRequest
import android.os.Bundle
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.sync.SyncDataType
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    @ApplicationContext private val context: Context,
    private val localAddressBookStore: Lazy<LocalAddressBookStore>,
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
        logger.fine("Enabling sync framework for account=$account, authority=$authority")
        if (ContentResolver.getIsSyncable(account, authority) != 1)
            ContentResolver.setIsSyncable(account, authority, 1)
    }

    /**
     * Disable this account/provider to be syncable.
     *
     * If an authority is not syncable, this implies that there's no sync on content changes, too.
     */
    fun disableSyncAbility(account: Account, authority: String) {
        logger.fine("Disabling sync framework for account=$account, authority=$authority")
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
     *
     * This implies that the [authority] is syncable, so this method makes the [authority]
     * syncable if required.
     */
    fun enableSyncOnContentChange(account: Account, authority: String) {
        if (!isSyncable(account, authority))
            enableSyncAbility(account, authority)

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
     * Cancels the sync request in the Sync Framework for Android 14+.
     * This is a workaround for the bug that the sync framework does not handle pending syncs correctly
     * on Android 14+ (API level 34+).
     *
     * See: https://github.com/bitfireAT/davx5-ose/issues/1458
     *
     * @param account The account for which the sync request should be canceled.
     * @param authority The authority for which the sync request should be canceled.
     * @param extras The original extras Bundle used to start the sync.
     */
    fun cancelSync(account: Account, authority: String, extras: Bundle) {
        // Recreate the sync request which was used to start this sync
        val syncRequest = SyncRequest.Builder()
            .setSyncAdapter(account, authority)
            .setExtras(extras)
            .syncOnce()
            .build()

        // Cancel it
        ContentResolver.cancelSync(syncRequest)
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
     * @param authority sync authority (like [android.provider.CalendarContract.AUTHORITY])
     * @return whether the content triggered sync was enabled successfully
     */
    @WorkerThread
    private fun setSyncOnContentChange(account: Account, authority: String, enable: Boolean): Boolean {
        logger.fine("Setting content-triggered syncs (sync framework) for account=$account, authority=$authority to enable=$enable")
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
     * @param authority sync authority (like [android.provider.CalendarContract.AUTHORITY])
     * @return whether the content triggered sync was enabled successfully
     */
    private fun setContentTrigger(account: Account, authority: String, enable: Boolean): Boolean =
        if (enable) {
            ContentResolver.setSyncAutomatically(account, authority, true)
            /* return */ ContentResolver.getSyncAutomatically(account, authority)
        } else {
            ContentResolver.setSyncAutomatically(account, authority, false)
            /* return */ !ContentResolver.getSyncAutomatically(account, authority)
        }

    /**
     * Observe whether any of the given data types is currently pending for sync.
     *
     * Note: On Android 14+ finished syncs stay by default pending. This is why we
     * explicitly cancel the active sync in [SyncAdapterImpl] for Android 14+. Doing
     * so allows us to have a reliable "pending" flag again, which is used in this method.
     *
     * @param account   account to observe sync status for
     * @param dataTypes data types to observe sync status for
     *
     * @return flow emitting true if any of the given data types has a sync pending, false otherwise
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun isSyncPending(account: Account, dataTypes: Iterable<SyncDataType>): Flow<Boolean> {
        val authorities = dataTypes.mapNotNull { dataType ->
            dataType.selectedAuthority(context)
        }

        // Use address book accounts if needed
        val accountsFlow = if (dataTypes.contains(SyncDataType.CONTACTS))
            localAddressBookStore.get().getAddressBookAccountsFlow(account)
        else
            flowOf(listOf(account))

        // Observe sync pending state for the given accounts and authorities
        return accountsFlow.flatMapLatest { accounts ->
            callbackFlow {
                // Observe sync pending state
                val listener = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_PENDING) {
                    trySend(anyPendingSync(accounts, authorities))
                }

                // Emit initial value
                trySend(anyPendingSync(accounts, authorities))

                // Clean up listener on close
                awaitClose { ContentResolver.removeStatusChangeListener(listener) }
            }
        }.distinctUntilChanged()
    }

    /**
     * Check if any of the given accounts and authorities have a sync pending.
     *
     * @param accounts  accounts to check sync status for
     * @param authorities authorities to check sync status for
     *
     * @return true if any of the given accounts and authorities has a sync pending, false otherwise
     */
    private fun anyPendingSync(accounts: List<Account>, authorities: List<String>): Boolean =
        accounts.any { account ->
            authorities.any { authority ->
                ContentResolver.isSyncPending(account, authority).also { pending ->
                    logger.finer("Sync pending($account, $authority) = $pending")
                }
            }
        }

}