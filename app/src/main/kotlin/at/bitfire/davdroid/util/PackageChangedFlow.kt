package at.bitfire.davdroid.util

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import at.bitfire.davdroid.log.Logger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun broadcastReceiverFlow(context: Context, filter: IntentFilter): Flow<Intent> = callbackFlow {
    val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent)
        }
    }

    // register receiver
    Logger.log.fine("Registering broadcast receiver for $filter")
    context.registerReceiver(receiver, filter)

    // wait until flow is cancelled, then clean up
    awaitClose {
        Logger.log.fine("Unregistering broadcast receiver for $filter")
        context.unregisterReceiver(receiver)
    }
}

fun packageChangedFlow(context: Context): Flow<Intent> {
    val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
        addAction(Intent.ACTION_PACKAGE_CHANGED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addDataScheme("package")
    }
    return broadcastReceiverFlow(context, filter)
}