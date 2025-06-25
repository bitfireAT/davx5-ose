/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.SyncRequest
import android.provider.CalendarContract
import androidx.test.filters.LargeTest
import at.bitfire.davdroid.di.DefaultDispatcher
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AndroidSyncFrameworkTest {

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    lateinit var account: Account


    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccount.create()

        
        ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)
    }


    @LargeTest
    @Test
    fun testOnPerformSync_syncAlwaysPending() = runTest {
        // This test is expected to fail on Android 13 and below (needs cold boot, or longer run time otherwise).
        // It succeeds on Android 14+ where the sync framework always pending bug is present and hopefully fails
        // as soon as the bug is fixed in a future android version.
        // See https://github.com/bitfireAT/davx5-ose/issues/1458

        // Disable the workaround we put in place
        mockkStatic(ContentResolver::class)
        every { ContentResolver.cancelSync(any()) } just runs

        withContext(Dispatchers.Default.limitedParallelism(1)) {
            // Request calendar sync
            ContentResolver.requestSync(
                SyncRequest.Builder()
                    .setSyncAdapter(account, CalendarContract.AUTHORITY)
                    .syncOnce()
                    .build()
            )

            // Verify the sync keeps being pending for the next 55 seconds
            repeat(55) {
                assertTrue(ContentResolver.isSyncPending(account, CalendarContract.AUTHORITY))
                delay(1000) // wait a bit before checking again
            }
        }
    }

}