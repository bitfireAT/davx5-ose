/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class FakeSyncAdapter @Inject constructor(
    @ApplicationContext context: Context,
    private val logger: Logger
): AbstractThreadedSyncAdapter(context, true), SyncAdapter {

    init {
        logger.info("FakeSyncAdapter created")
    }

    override fun onPerformSync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
        logger.log(
            Level.INFO,
            "onPerformSync(account=$account, extras=$extras, authority=$authority, syncResult=$syncResult)",
            extras.keySet().map { key -> "extras[$key] = ${extras[key]}" }
        )

        // fake 5 sec sync
        try {
            Thread.sleep(5000)
        } catch (_: InterruptedException) {
            logger.info("onPerformSync($account) cancelled")
        }

        logger.info("onPerformSync($account) finished")
    }


    // SyncAdapter implementation and Hilt module

    override fun getBinder(): IBinder = syncAdapterBinder

}