/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.logging.Logger

/**
 * Creates a flow that emits the respective [Intent] when a broadcast is received.
 *
 * @param context the context to register the receiver with
 * @param filter  specifies which broadcasts shall be received
 * @param flags   flags to pass to [Context.registerReceiver] (usually [ContextCompat.RECEIVER_EXPORTED] or
 * [ContextCompat.RECEIVER_NOT_EXPORTED]; `null` if only system broadcasts are received)
 * @param immediate if `true`, send an empty [Intent] as first value
 *
 * @return cold flow of [Intent]s
 */
@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun broadcastReceiverFlow(
    context: Context,
    filter: IntentFilter,
    flags: Int? = null,
    immediate: Boolean
): Flow<Intent> = callbackFlow {
    val logger = Logger.getGlobal()

    val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            logger.fine("broadcastReceiverFlow received $intent")
            trySend(intent)
        }
    }

    // register receiver
    var filterDump = filter.toString()
    filter.dump({ filterDump = it }, "")
    logger.fine("Registering broadcast receiver for $filterDump (flags=$flags)")
    if (flags != null)
        ContextCompat.registerReceiver(context, receiver, filter, null, null, flags)
    else
        context.registerReceiver(receiver, filter)

    // send empty Intent as first value, if requested
    if (immediate)
        trySend(Intent())

    // wait until flow is cancelled, then clean up
    awaitClose {
        logger.fine("Unregistering broadcast receiver for $filterDump")
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
    return broadcastReceiverFlow(context = context, filter = filter, immediate = immediate)
}