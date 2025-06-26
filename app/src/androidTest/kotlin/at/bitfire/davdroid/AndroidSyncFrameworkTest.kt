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
import androidx.test.filters.SdkSuppress
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

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


    @SdkSuppress(minSdkVersion = 34)
    @LargeTest
    @Test
    fun testVerifySyncAlwaysPending() = runBlocking {
        // We use runBlocking for this test because:
        // - It uses the default dispatcher which does not auto-advance virtual time
        //   and we need real system time to test the sync framework behavior.
        // - The test is expected to run for a long time and in order to catch and treat
        //   the timeout exception as success we can't use runTest, but need to create the
        //   timeout ourselves.

        // The test is expected to fail on Android 13 (API lvl 33) and below. It
        // succeeds on Android 14+ (API lvl 34), however, where the sync framework
        // always pending bug is present and hopefully fails as soon as the bug is
        // fixed in a future android version.
        // See https://github.com/bitfireAT/davx5-ose/issues/1458

        // Create a sync request for calendar authority
        val syncRequest = SyncRequest.Builder()
            .setSyncAdapter(account, CalendarContract.AUTHORITY)
            .syncOnce()
            .build()

        // Disable the workaround we put in place for Android 14+ present in
        // [SyncAdapterService.SyncAdapter.onPerformSync]
        mockkStatic(ContentResolver::class)
        every { ContentResolver.cancelSync(syncRequest) } just runs

        // Based on my observations the sync framework usually takes 40-63 seconds
        // to start the sync and change the sync pending state on Android 13 and
        // below (but could take up to several minutes).
        // On Android 14+ it will run the sync at some point, but report a pending
        // sync state forever. To verify this, we can wait for around a minute and
        // if the sync is still pending, we can assume that the bug is still present.
        try {
            withTimeout(65.seconds) {
                // Request calendar sync
                ContentResolver.requestSync(syncRequest)

                // Verify the sync keeps being pending "forever" (65 seconds in this test)
                while (true) {
                    assertTrue(ContentResolver.isSyncPending(account, CalendarContract.AUTHORITY))
                    delay(2000) // Check every 2 seconds
                }
            }
        } catch (_: TimeoutCancellationException) {
            // Expected outcome: sync stayed pending "forever"
        } finally {
            unmockkStatic(ContentResolver::class)
        }
    }

}