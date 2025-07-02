package at.bitfire.davdroid.util

import android.accounts.Account
import android.content.ContentResolver
import android.content.SyncRequest
import android.os.Bundle

object SyncFrameworkUtils {

    /**
     * Cancels the sync request in the Sync Framework for Android 14+.
     * This is a workaround for the bug that the sync framework does not handle pending syncs correctly
     * on Android 14+ (API level 34+).
     *
     * See: https://github.com/bitfireAT/davx5-ose/issues/1458
     *
     * @param account The account for which the sync request should be canceled.
     * @param authority The authority for which the sync request should be canceled.
     * @param upload Whether the sync request is for an upload operation.
     */
    fun cancelSyncInSyncFramework(account: Account, authority: String, upload: Boolean) {
        // Recreate the sync request used to start this sync
        val syncRequest = SyncRequest.Builder()
            .setSyncAdapter(account, authority)
            .setExtras(Bundle().apply {
                if (upload)
                    putBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, true)
            })
            .syncOnce()
            .build()

        // Cancel it
        ContentResolver.cancelSync(syncRequest)
    }
}