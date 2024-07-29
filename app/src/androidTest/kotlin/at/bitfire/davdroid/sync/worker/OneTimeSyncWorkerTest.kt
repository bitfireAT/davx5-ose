/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.accounts.Account
import android.content.Context
import android.provider.CalendarContract
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class OneTimeSyncWorkerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    lateinit var account: Account

    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccountAuthenticator.create()
    }

    @After
    fun tearDown() {
        TestAccountAuthenticator.remove(account)
    }


    @Test
    fun testEnqueue_enqueuesWorker() {
        OneTimeSyncWorker.enqueue(context, account, CalendarContract.AUTHORITY)
        val workerName = OneTimeSyncWorker.workerName(account, CalendarContract.AUTHORITY)
        assertTrue(TestUtils.workScheduledOrRunningOrSuccessful(context, workerName))
    }

}