/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentResolver
import android.content.SyncRequest
import android.os.Bundle
import android.provider.CalendarContract
import androidx.test.filters.SdkSuppress
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Reproduces the raw Android sync framework bug where a one-time sync's "pending" flag stays
 * `true` forever once it has been set, even after the sync has finished (on Android 14+).
 *
 * Uses [FakeSyncAdapter] - the default [at.bitfire.davdroid.sync.adapter.SyncAdapter] test binding
 * (see [at.bitfire.davdroid.di.FakeSyncAdapterModule]), a bare sync adapter that finishes normally
 * without applying any of DAVx5's workarounds - so this demonstrates the bug exists independently
 * of DAVx5's own code.
 *
 * DAVx5 works around this in [at.bitfire.davdroid.sync.adapter.SyncAdapterImpl] by explicitly
 * calling `ContentResolver.cancelSync(account, authority)` after every sync - see
 * `SyncAdapterImplTest.testOnPerformSync_clearsPendingFlag` for a test verifying that fix. It
 * can't be verified here: the [at.bitfire.davdroid.sync.adapter.SyncAdapter] implementation used
 * by the real sync framework dispatch is swapped for the whole test binary via `@TestInstallIn`,
 * which - unlike `@InstallIn` modules - Hilt does not allow undoing for just one test class.
 */
@HiltAndroidTest
class AndroidSyncFrameworkTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    lateinit var account: Account
    val authority = CalendarContract.AUTHORITY

    @Before
    fun setUp() {
        hiltRule.inject()
        account = TestAccount.create()

        // Enable sync globally and for the test account
        ContentResolver.setIsSyncable(account, authority, 1)
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)
    }


    /**
     * Reproduces the raw Android sync framework bug on Android 14+: once a one-time sync's
     * "pending" flag has been set (when the job was scheduled), nothing on the normal-completion
     * path ever clears it back to `false` again - see `SyncManager.cancelJob()` in AOSP, which
     * only cancels the JobScheduler job and never calls `SyncStorageEngine.markPending(false)` /
     * `setAuthorityPendingState()`.
     *
     * Introduced by AOSP commit `5ebdf21a7d3b` ("Put syncs in a dedicated job namespace",
     * Kweku Adams, 2022-12-22, landed for Android 14/API 34). It replaced the internal
     * `JobSchedulerInternal.getSystemScheduledPendingJobs()` (whose javadoc said "a running job
     * is not considered pending") with the public `JobScheduler.getAllPendingJobs()` (whose
     * javadoc says the opposite: "includes jobs that are currently started") inside
     * `SyncManager.getAllPendingSyncs()`/`setAuthorityPendingState()`. So the one-time "is this
     * still pending" recompute done right after dispatch now always sees the just-started job as
     * still pending, and nothing ever corrects it afterwards. Confirmed absent on Android 13 and
     * earlier (diffed `android-13.0.0_r62` vs. `android-14.0.0_r1` in AOSP).
     *
     * No upper SDK bound on purpose: if this ever starts failing on some future Android version,
     * that's the platform bug having been fixed there, not a broken test - see the assertion
     * message.
     */
    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun testSyncStaysPendingAfterFinish_rawFrameworkBug() = runBlocking {
        val syncRequest = SyncRequest.Builder()
            .setSyncAdapter(account, authority)
            .syncOnce()
            .setExtras(Bundle())    // needed for Android 9
            .setManual(true)        // equivalent of setting both SYNC_EXTRAS_IGNORE_SETTINGS and SYNC_EXTRAS_IGNORE_BACKOFF
            .build()
        ContentResolver.requestSync(syncRequest)

        // Rather than asserting on the exact sequence of intermediate state changes (which is
        // prone to flakiness - callbacks can be coalesced or arrive with unpredictable timing),
        // we only wait for two clear-cut, unambiguous points in time: the sync starting (using
        // isSyncActive, which - unlike isSyncPending - is reliably set/cleared by the framework),
        // and the sync ending. What we actually want to test is the state *after* that point.
        withTimeout(60.seconds) { // Usually takes less than 30 seconds
            while (!ContentResolver.isSyncActive(account, authority))
                delay(200)
            while (ContentResolver.isSyncActive(account, authority))
                delay(200)
        }

        assertTrue(
            "Sync framework bug now fixed? Expected the sync to still be pending after finishing " +
                    "(this is the known AOSP \"always pending\" bug DAVx5 works around in SyncAdapterImpl) " +
                    "- if this fails, the platform bug may have been fixed on this Android version; " +
                    "investigate before removing the workaround.",
            ContentResolver.isSyncPending(account, authority)
        )
    }


    companion object {

        var globalAutoSyncBeforeTest = false

        @BeforeClass
        @JvmStatic
        fun before() {
            globalAutoSyncBeforeTest = ContentResolver.getMasterSyncAutomatically()

            // We'll request syncs explicitly and with SYNC_EXTRAS_IGNORE_SETTINGS
            ContentResolver.setMasterSyncAutomatically(false)
        }

        @AfterClass
        @JvmStatic
        fun after() {
            ContentResolver.setMasterSyncAutomatically(globalAutoSyncBeforeTest)
        }

    }

}
