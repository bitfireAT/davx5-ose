/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import at.bitfire.davdroid.log.Logger
import com.google.common.util.concurrent.ListenableFuture

object TestUtils {

    val targetApplication by lazy { InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application }

    fun workScheduledOrRunning(context: Context, workerName: String): Boolean {
        val future: ListenableFuture<List<WorkInfo>> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(workerName)
        val workInfoList: List<WorkInfo>
        try {
            workInfoList = future.get()
        } catch (e: Exception) {
            Logger.log.severe("Failed to retrieve work info list for worker $workerName", )
            return false
        }
        for (workInfo in workInfoList) {
            val state = workInfo.state
            if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED)
                return true
        }
        return false
    }

}