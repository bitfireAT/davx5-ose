/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import org.jetbrains.annotations.TestOnly
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.abs

object TestUtils {

    fun assertWithin(expected: Long, actual: Long, tolerance: Long) {
        val absDifference = abs(expected - actual)
        assertTrue(
            "$actual not within ($expected ± $tolerance)",
            absDifference <= tolerance
        )
    }

    @TestOnly
    fun workScheduledOrRunning(context: Context, workerName: String): Boolean =
        workInStates(context, workerName, listOf(
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING
        ))

    @TestOnly
    fun workScheduledOrRunningOrSuccessful(context: Context, workerName: String): Boolean =
        workInStates(context, workerName, listOf(
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING,
            WorkInfo.State.SUCCEEDED
        ))

    @TestOnly
    fun workInStates(context: Context, workerName: String, states: List<WorkInfo.State>): Boolean =
        WorkManager.getInstance(context).getWorkInfos(WorkQuery.Builder
            .fromUniqueWorkNames(listOf(workerName))
            .addStates(states)
            .build()
        ).get().isNotEmpty()


    /* Copyright 2019 Google LLC.
    SPDX-License-Identifier: Apache-2.0 */
    @TestOnly
    fun <T> LiveData<T>.getOrAwaitValue(
        time: Long = 2,
        timeUnit: TimeUnit = TimeUnit.SECONDS
    ): T {
        var data: T? = null
        val latch = CountDownLatch(1)
        val observer = object : Observer<T> {
            override fun onChanged(value: T) {
                data = value
                latch.countDown()
                this@getOrAwaitValue.removeObserver(this)
            }
        }

        this.observeForever(observer)

        // Don't wait indefinitely if the LiveData is not set.
        if (!latch.await(time, timeUnit)) {
            throw TimeoutException("LiveData value was never set.")
        }

        @Suppress("UNCHECKED_CAST")
        return data as T
    }

}