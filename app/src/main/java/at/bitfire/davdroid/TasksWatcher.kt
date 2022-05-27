/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.content.Context
import android.content.Intent
import at.bitfire.davdroid.syncadapter.SyncUtils.updateTaskSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TasksWatcher protected constructor(
    context: Context
): PackageChangedReceiver(context) {

    companion object {

        fun watch(context: Context) = TasksWatcher(context)

    }


    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.Default).launch {
            updateTaskSync(context)
        }
    }

}
