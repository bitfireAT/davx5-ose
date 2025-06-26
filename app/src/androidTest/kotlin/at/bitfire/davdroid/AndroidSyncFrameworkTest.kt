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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.time.Duration

@HiltAndroidTest
class AndroidSyncFrameworkTest {

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    lateinit var account: Account

    private var masterSyncStateBeforeTest = ContentResolver.getMasterSyncAutomatically()

    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccount.create()

        ContentResolver.setMasterSyncAutomatically(true)
        ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)
        ContentResolver.setMasterSyncAutomatically(masterSyncStateBeforeTest)
    }


    @LargeTest
    @Test
    fun testVerifySyncAlwaysPending() = runTest(
        // The test is expected to run for a long time, so we increase the timeout
        timeout = Duration.parse("70s")
    ) {
        // This test is expected to fail on Android 13 and below and usually does so (sometimes only after
        // a cold boot). It succeeds on Android 14+, however, where the sync framework always pending bug
        // is present and hopefully fails as soon as the bug is fixed in a future android version.
        // See https://github.com/bitfireAT/davx5-ose/issues/1458

        // Disable the workaround we put in place for Android 14+
        mockkStatic(ContentResolver::class)
        every { ContentResolver.cancelSync(any()) } just runs

        // The test does not run on the injectable default dispatcher as to not forward virtual time
        withContext(Dispatchers.Default) {

            // Request calendar sync
            ContentResolver.requestSync(
                SyncRequest.Builder()
                    .setSyncAdapter(account, CalendarContract.AUTHORITY)
                    .syncOnce()
                    .build()
            )

            // Based on my observations the sync framework usually takes 40-63 seconds
            // to start the sync (could be up to several minutes) and change the sync
            // pending state (only on Android 13 and below).
            // On Android 14+ it will run the sync but remain in pending state forever.
            // To verify this, we can wait for around a minute and if the sync is still
            // pending, we can assume that the bug is still present.

            // Verify the sync keeps being pending "forever" (65 seconds in this test)
            repeat(65) {
                assertTrue(ContentResolver.isSyncPending(account, CalendarContract.AUTHORITY))
                delay(1000)
            }
        }
    }

}