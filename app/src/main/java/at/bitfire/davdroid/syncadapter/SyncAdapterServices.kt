/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import java.util.logging.Level

abstract class SyncAdapterService: Service() {

    fun syncAdapter() = SyncAdapter(this)

    override fun onBind(intent: Intent?) = syncAdapter().syncAdapterBinder!!

    /**
     * Entry point for the sync adapter framework.
     *
     * Handles incoming sync requests from the sync adapter framework.
     *
     * Although we do not use the sync adapter for syncing anymore, we keep this sole
     * adapter to provide exported services, which allow android system components and calendar,
     * contacts or task apps to sync via DAVx5.
     */
    class SyncAdapter(
        context: Context
    ): AbstractThreadedSyncAdapter(
        context,
        true    // isSyncable shouldn't be -1 because DAVx5 sets it to 0 or 1.
                           // However, if it is -1 by accident, set it to 1 to avoid endless sync loops.
    ) {

        override fun onPerformSync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            // We seem to have to pass this old SyncFramework extra for an Android 7 workaround
            val upload = extras.containsKey(ContentResolver.SYNC_EXTRAS_UPLOAD)
            Logger.log.info("Sync request via sync adapter (upload=$upload)")

            // Should we run the sync at all?
            if (!SyncWorker.wifiConditionsMet(context, AccountSettings(context, account))) {
                Logger.log.info("Sync conditions not met. Aborting sync adapter")
                return
            }

            Logger.log.fine("Sync adapter now handing over to SyncWorker")
            SyncWorker.enqueue(context, account, authority, upload = upload)
        }

        override fun onSecurityException(account: Account, extras: Bundle, authority: String, syncResult: SyncResult) {
            Logger.log.log(Level.WARNING, "Security exception for $account/$authority")
        }

        override fun onSyncCanceled() {
            Logger.log.info("Ignoring sync adapter cancellation")
            super.onSyncCanceled()
        }

        override fun onSyncCanceled(thread: Thread) {
            Logger.log.info("Ignoring sync adapter cancellation")
            super.onSyncCanceled(thread)
        }

    }

}

// exported sync adapter services; we need a separate class for each authority
class AddressBooksSyncAdapterService: SyncAdapterService()
class CalendarsSyncAdapterService: SyncAdapterService()
class ContactsSyncAdapterService: SyncAdapterService()
class JtxSyncAdapterService: SyncAdapterService()
class OpenTasksSyncAdapterService: SyncAdapterService()
class TasksOrgSyncAdapterService: SyncAdapterService()
