/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.MainThread

abstract class PackageChangedReceiver(
    val context: Context
): BroadcastReceiver(), AutoCloseable {

    /**
     * Registers the receiver.
     *
     * @param whether [onPackageChanged] shall be called immediately after registering
     */
    fun register(immediateCall: Boolean = false) {
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        context.registerReceiver(this, filter)

        if (immediateCall)
            onPackageChanged()
    }

    override fun close() {
        context.unregisterReceiver(this)
    }


    @MainThread
    abstract fun onPackageChanged()

    override fun onReceive(context: Context, intent: Intent) {
        onPackageChanged()
    }

}