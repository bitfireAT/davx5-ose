/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerFactory
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertTrue
import kotlin.math.abs

object TestUtils {

    fun assertWithin(expected: Long, actual: Long, tolerance: Long) {
        val absDifference = abs(expected - actual)
        assertTrue(
            "$actual not within ($expected ± $tolerance)",
            absDifference <= tolerance
        )
    }

    /**
     * Initializes WorkManager for instrumentation tests.
     */
    fun setUpWorkManager(context: Context, workerFactory: WorkerFactory? = null) {
        val config = Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG)
        if (workerFactory != null)
            config.setWorkerFactory(workerFactory)
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config.build())
    }

    fun workInStates(context: Context, workerName: String, states: List<WorkInfo.State>): Boolean =
        WorkManager.getInstance(context).getWorkInfos(WorkQuery.Builder
            .fromUniqueWorkNames(listOf(workerName))
            .addStates(states)
            .build()
        ).get().isNotEmpty()

    fun workScheduledOrRunning(context: Context, workerName: String): Boolean =
        workInStates(context, workerName, listOf(
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING
        ))

    fun workScheduledOrRunningOrSuccessful(context: Context, workerName: String): Boolean =
        workInStates(context, workerName, listOf(
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING,
            WorkInfo.State.SUCCEEDED
        ))

}