/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.content.Context
import android.provider.CalendarContract
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.sync.SyncManagerTest.Companion.account
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class OneTimeSyncWorkerTest {

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun testEnqueue_enqueuesWorker() {
        OneTimeSyncWorker.enqueue(context, account, CalendarContract.AUTHORITY)
        val workerName = OneTimeSyncWorker.workerName(account, CalendarContract.AUTHORITY)
        Assert.assertTrue(TestUtils.workScheduledOrRunningOrSuccessful(context, workerName))
    }

}