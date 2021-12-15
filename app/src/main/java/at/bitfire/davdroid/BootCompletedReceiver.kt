/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import at.bitfire.davdroid.log.Logger

/**
 * There are circumstances when Android drops automatic sync of accounts and resets them
 * to manual synchronization, for instance when the device is booted into safe mode.
 *
 * This receiver causes the app to be started when the device is rebooted. When the app
 * is started, it checks (and repairs, if necessary) the sync intervals in [App.onCreate].
 */
class BootCompletedReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Logger.log.info("Device has been rebooted; checking sync intervals etc.")
        // sync intervals are checked in App.onCreate()
    }

}