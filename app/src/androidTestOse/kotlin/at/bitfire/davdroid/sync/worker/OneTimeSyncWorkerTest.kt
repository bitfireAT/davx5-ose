/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.provider.CalendarContract
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.settings.AccountSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidTest
class OneTimeSyncWorkerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    private val accountManager by lazy { AccountManager.get(context) }
    private val account by lazy { Account("SyncManagerTest", context.getString(R.string.account_type)) }

    @Before
    fun setup() {
        hiltRule.inject()

        // Create test account
        assertTrue(accountManager.addAccountExplicitly(account, "test", AccountSettings.initialUserData(Credentials("test", "test"))))
    }

    @After
    fun teardown() {
        assertTrue(accountManager.removeAccount(account, null, null).getResult(10, TimeUnit.SECONDS))
    }


    @Test
    fun testEnqueue_enqueuesWorker() {
        OneTimeSyncWorker.enqueue(context, account, CalendarContract.AUTHORITY)
        val workerName = OneTimeSyncWorker.workerName(account, CalendarContract.AUTHORITY)
        assertTrue(TestUtils.workScheduledOrRunningOrSuccessful(context, workerName))
    }

}