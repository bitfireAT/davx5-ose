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

/**
 * Creates a flow that emits the respective [Intent] when a broadcast is received.
 *
 * @param context the context to register the receiver with
 * @param filter  specifies which broadcasts shall be received
 * @param immediate if `true`, send an empty [Intent] as first value
 *
 * @return cold flow of [Intent]s
 */
@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun broadcastReceiverFlow(context: Context, filter: IntentFilter, immediate: Boolean = true): Flow<Intent> = callbackFlow {
    val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent)
        }
    }

    // register receiver
    var filterDump = filter.toString()
    filter.dump({ filterDump = it }, "")
    Logger.log.fine("Registering broadcast receiver for $filterDump")
    context.registerReceiver(receiver, filter)

    // send empty Intent as first value, if requested
    if (immediate)
        trySend(Intent())

    // wait until flow is cancelled, then clean up
    awaitClose {
        Logger.log.fine("Unregistering broadcast receiver for $filterDump")
        context.unregisterReceiver(receiver)
    }
}

/**
 * Creates a flow that emits the Intent when a package is added, changed or removed.
 *
 * @param context the context to register the receiver with
 * @param immediate if `true`, send an empty [Intent] as first value
 *
 * @return cold flow of [Intent]s
 */
fun packageChangedFlow(context: Context, immediate: Boolean = true): Flow<Intent> {
    val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
        addAction(Intent.ACTION_PACKAGE_CHANGED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addDataScheme("package")
    }
    return broadcastReceiverFlow(context, filter, immediate)
}